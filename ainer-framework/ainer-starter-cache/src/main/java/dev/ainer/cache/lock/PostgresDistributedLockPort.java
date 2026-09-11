package dev.ainer.cache.lock;

import dev.ainer.core.uuid.Uuidv7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL 会话级 advisory lock 实现（ADR-0039 §4 的降级实现：{@code pg_try_advisory_lock(hash(key))}）。
 * 适用于「没有 Redis 但有多实例部署 + 已有 PostgreSQL」的产品：锁的真实互斥范围是
 * <strong>同一个数据库实例</strong>，不再退化为进程内锁。
 *
 * <h2>每锁一条连接（池占用代价）</h2>
 * {@code pg_try_advisory_lock} 是<strong>会话级</strong>锁：锁的宿主是持有它的那条连接，
 * 连接断开（进程崩溃、连接被服务端终止）时 PostgreSQL 会自动放锁——这正是崩溃自动恢复的来源，
 * 但也意味着<strong>每个被持有的锁都会独占一条池化连接，直到释放或 TTL 到期</strong>。
 * 因此：同时持有的锁数量 ≤ 连接池上限；池会被长持有锁吃满，调用方必须
 * ① 把 TTL 设成与业务时长同量级的短值，② 在 {@code finally} 中释放，
 * ③ 按「并发锁数 + 常规查询并发」评估池大小。需要大量并发锁时应改用 Redis 实现。
 *
 * <h2>key → bigint 哈希碰撞的可接受性</h2>
 * advisory lock 的 key 空间是 64 位整数，本实现用 {@code hashtextextended(key, seed)} 把文本 key
 * 折叠进去。不同文本 key 理论上可能映射到同一个 bigint，后果是<strong>过度互斥</strong>
 * （两个无关业务互相阻塞），而<strong>不会</strong>让同一个 key 出现两个持有者——即碰撞只会降低并发度，
 * 不会破坏互斥正确性。按生日界估计，需要约 2<sup>32</sup> 个不同 key 才有 50% 碰撞概率，
 * 而每把锁都占用一条连接，实际规模远小于此，因此碰撞风险可接受。
 *
 * <h2>TTL 语义</h2>
 * PostgreSQL advisory lock 本身没有 TTL。TTL 由实例内的收割线程（{@code reapInterval} 周期）
 * 落实：超过到期时间的持有会被 {@code pg_advisory_unlock} 主动放锁并归还连接。
 * 每个实例只收割自己登记的持有，因此某实例崩溃后，其连接断开会让 PostgreSQL 立即放锁，
 * 不需要等 TTL。
 */
public final class PostgresDistributedLockPort implements DistributedLockPort, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDistributedLockPort.class);

    /** {@code hashtextextended} 的固定 seed：同一 key 必须永远折叠到同一 bigint，跨实例才能互斥。 */
    private static final long HASH_SEED = 39_0039L;

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(hashtextextended(?, ?))";

    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(hashtextextended(?, ?))";

    private static final Duration DEFAULT_REAP_INTERVAL = Duration.ofSeconds(1);

    private final DataSource dataSource;
    private final Clock clock;
    private final Duration reapInterval;
    private final ConcurrentHashMap<String, Held> held = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reaper;

    /** 使用 1 秒 TTL 收割周期与系统 UTC 时钟。 */
    public PostgresDistributedLockPort(DataSource dataSource) {
        this(dataSource, DEFAULT_REAP_INTERVAL, Clock.systemUTC());
    }

    /**
     * @param dataSource   提供「每锁一条连接」的数据源
     * @param reapInterval TTL 收割周期，必须为正数
     */
    public PostgresDistributedLockPort(DataSource dataSource, Duration reapInterval) {
        this(dataSource, reapInterval, Clock.systemUTC());
    }

    /**
     * @param dataSource   提供「每锁一条连接」的数据源
     * @param reapInterval TTL 收割周期，必须为正数
     * @param clock        用于 TTL 判定与测试可控时钟
     */
    public PostgresDistributedLockPort(DataSource dataSource, Duration reapInterval, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(reapInterval, "reapInterval");
        if (reapInterval.isNegative() || reapInterval.isZero()) {
            throw new IllegalArgumentException("reapInterval 必须为正数，当前为 " + reapInterval);
        }
        this.reapInterval = reapInterval;
        this.reaper = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("ainer-pg-lock-reaper").factory());
        long periodMillis = Math.max(reapInterval.toMillis(), 1L);
        this.reaper.scheduleWithFixedDelay(
                this::reapExpiredSafely, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public Optional<LockHandle> tryLock(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl 必须为正数，当前为 " + ttl);
        }
        Connection connection = null;
        try {
            connection = this.dataSource.getConnection();
            // 会话级 advisory lock 与事务无关；显式 autoCommit 避免这条长期持有的连接停留在
            // idle-in-transaction（会阻塞 vacuum 并让池的 statement timeout 生效）。
            connection.setAutoCommit(true);
            if (!tryAdvisoryLock(connection, key)) {
                closeQuietly(connection);
                return Optional.empty();
            }
            LockHandle handle = new LockHandle(key, Uuidv7.generate().toString());
            Held previous = this.held.put(key,
                    new Held(connection, handle, this.clock.millis() + ttl.toMillis()));
            if (previous != null) {
                // 本实例登记过同 key 的旧持有，但 PostgreSQL 端已放锁（例如旧连接被服务端终止）。
                // 旧连接不再持有任何东西，直接归还池，避免连接泄漏。
                LOGGER.warn("[ainer-cache] 同一实例内 key={} 存在未收割的旧持有，"
                        + "已归还其连接（PostgreSQL 端此前已放锁）", key);
                closeQuietly(previous.connection());
            }
            return Optional.of(handle);
        } catch (SQLException ex) {
            closeQuietly(connection);
            throw new IllegalStateException("获取 PostgreSQL advisory lock 失败：key=" + key, ex);
        }
    }

    @Override
    public void release(LockHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Held entry = this.held.get(handle.key());
        if (entry == null || !entry.handle().token().equals(handle.token())) {
            // no-op：不是本实例持有的锁，或已被 TTL 收割 / 已被新持有者接管
            return;
        }
        if (!this.held.remove(handle.key(), entry)) {
            // 收割线程刚刚回收了它
            return;
        }
        releaseHeld(entry);
    }

    /** 当前未过期的持有数（诊断/测试用）。 */
    public int activeHoldCount() {
        long now = this.clock.millis();
        return (int) this.held.values().stream()
                .filter(entry -> entry.deadlineMillis() > now)
                .count();
    }

    /** 停止收割线程并释放全部持有（Spring 关闭上下文时自动调用）。 */
    @Override
    public void close() {
        this.reaper.shutdownNow();
        List<Held> remaining = List.copyOf(this.held.values());
        this.held.clear();
        for (Held entry : remaining) {
            releaseHeld(entry);
        }
    }

    private void reapExpiredSafely() {
        try {
            reapExpired();
        } catch (RuntimeException ex) {
            // 收割线程不能因为单次异常而终止，否则 TTL 语义静默失效
            LOGGER.warn("[ainer-cache] PostgreSQL advisory lock TTL 收割异常，将在下个周期重试", ex);
        }
    }

    private void reapExpired() {
        long now = this.clock.millis();
        for (var entry : this.held.entrySet()) {
            Held value = entry.getValue();
            if (value.deadlineMillis() > now) {
                continue;
            }
            if (!this.held.remove(entry.getKey(), value)) {
                continue;
            }
            LOGGER.warn("[ainer-cache] PostgreSQL advisory lock 已超过 TTL（收割周期 {}），"
                    + "主动放锁并归还连接：key={}", this.reapInterval, value.handle().key());
            releaseHeld(value);
        }
    }

    private void releaseHeld(Held entry) {
        try {
            unlock(entry.connection(), entry.handle().key());
        } catch (SQLException ex) {
            LOGGER.warn("[ainer-cache] pg_advisory_unlock 执行失败，改为直接归还连接"
                    + "（连接关闭时 PostgreSQL 会放掉该会话的全部 advisory lock）：key={}",
                    entry.handle().key(), ex);
        } finally {
            closeQuietly(entry.connection());
        }
    }

    private static boolean tryAdvisoryLock(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK_SQL)) {
            statement.setString(1, key);
            statement.setLong(2, HASH_SEED);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UNLOCK_SQL)) {
            statement.setString(1, key);
            statement.setLong(2, HASH_SEED);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && !resultSet.getBoolean(1)) {
                    LOGGER.debug("[ainer-cache] pg_advisory_unlock 返回 false（该会话未持有 key={}）", key);
                }
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            LOGGER.warn("[ainer-cache] 归还 advisory lock 连接失败", ex);
        }
    }

    private record Held(Connection connection, LockHandle handle, long deadlineMillis) {
    }
}
