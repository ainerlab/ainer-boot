package dev.ainer.cache.lock;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * PostgreSQL advisory lock 集成测试（Testcontainers {@code postgres:18.3-alpine}，与仓库镜像基线一致）。
 *
 * <p>验证 ADR-0039 §4 承诺的降级实现真实成立：
 * <ul>
 *   <li>两个独立 {@link PostgresDistributedLockPort}（模拟两个 JVM，各自独立数据库会话）互斥；</li>
 *   <li>释放后可以重新获取；用错误 token 释放是 no-op；</li>
 *   <li>TTL 到期由实例内收割器主动放锁（PostgreSQL advisory lock 自身没有 TTL）；</li>
 *   <li>「每个持有的锁独占一条连接」——用 {@code pg_stat_activity} / {@code pg_locks} 直接核对，
 *       失败获取不会泄漏连接。</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDistributedLockPortIntegrationTest {

    private static final String APPLICATION_NAME = "ainer-lock-it";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_lock_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    /** 每次 getConnection 都开新会话、close 真正关闭——正好用来观察「每锁一条连接」。 */
    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setApplicationName(APPLICATION_NAME);
        return dataSource;
    }

    @Test
    void secondInstanceCannotAcquireHeldLockAndConnectionsAreAccounted() throws SQLException {
        DataSource dataSource = dataSource();
        PostgresDistributedLockPort firstInstance = new PostgresDistributedLockPort(dataSource);
        PostgresDistributedLockPort secondInstance = new PostgresDistributedLockPort(dataSource);
        try {
            DistributedLockPort.LockHandle firstKey =
                    firstInstance.tryLock("it:key-1", Duration.ofSeconds(30)).orElseThrow();
            // 每个持有的锁独占一条连接
            assertThat(advisorySessions()).isEqualTo(1);
            assertThat(advisoryLocks()).isEqualTo(1);

            DistributedLockPort.LockHandle secondKey =
                    firstInstance.tryLock("it:key-2", Duration.ofSeconds(30)).orElseThrow();
            assertThat(firstInstance.activeHoldCount()).isEqualTo(2);
            assertThat(advisorySessions()).isEqualTo(2);
            assertThat(advisoryLocks()).isEqualTo(2);

            // 第二个实例（另一个数据库会话）拿不到同一把锁
            assertThat(secondInstance.tryLock("it:key-1", Duration.ofSeconds(30))).isEmpty();
            // 失败获取不留下连接
            assertThat(advisorySessions()).isEqualTo(2);
            // 不同 key 互不影响
            Optional<DistributedLockPort.LockHandle> otherKey =
                    secondInstance.tryLock("it:key-other", Duration.ofSeconds(30));
            assertThat(otherKey).isPresent();
            assertThat(advisorySessions()).isEqualTo(3);

            firstInstance.release(firstKey);
            firstInstance.release(secondKey);
            secondInstance.release(otherKey.orElseThrow());
            assertThat(firstInstance.activeHoldCount()).isZero();
            assertThat(advisorySessions()).isZero();
            assertThat(advisoryLocks()).isZero();

            // 释放后第二个实例可以重新获取
            assertThat(secondInstance.tryLock("it:key-1", Duration.ofSeconds(30))).isPresent();
        } finally {
            firstInstance.close();
            secondInstance.close();
        }
    }

    @Test
    void releaseWithForeignTokenIsNoOp() throws SQLException {
        DataSource dataSource = dataSource();
        PostgresDistributedLockPort holder = new PostgresDistributedLockPort(dataSource);
        PostgresDistributedLockPort other = new PostgresDistributedLockPort(dataSource);
        try {
            DistributedLockPort.LockHandle held = holder.tryLock("it:token", Duration.ofSeconds(30)).orElseThrow();

            // 不是自己的 token（也不能是过期后新持有者的锁）：no-op
            other.release(new DistributedLockPort.LockHandle("it:token", "not-my-token"));
            assertThat(other.tryLock("it:token", Duration.ofSeconds(30))).isEmpty();
            assertThat(advisoryLocks()).isEqualTo(1);

            holder.release(held);
            assertThat(other.tryLock("it:token", Duration.ofSeconds(30))).isPresent();
        } finally {
            holder.close();
            other.close();
        }
    }

    @Test
    void ttlReaperReleasesExpiredHold() throws SQLException {
        DataSource dataSource = dataSource();
        PostgresDistributedLockPort holder =
                new PostgresDistributedLockPort(dataSource, Duration.ofMillis(50));
        PostgresDistributedLockPort other = new PostgresDistributedLockPort(dataSource);
        try {
            holder.tryLock("it:ttl", Duration.ofMillis(200)).orElseThrow();
            assertThat(other.tryLock("it:ttl", Duration.ofSeconds(5))).isEmpty();
            assertThat(advisoryLocks()).isEqualTo(1);

            // PostgreSQL advisory lock 自身没有 TTL：到期由实例内收割器放锁（含归还连接）
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(holder.activeHoldCount()).isZero();
                assertThat(advisoryLocks()).isZero();
            });
            assertThat(other.tryLock("it:ttl", Duration.ofSeconds(5))).isPresent();
        } finally {
            holder.close();
            other.close();
        }
    }

    @Test
    void closingPortReleasesHeldLocks() throws SQLException {
        DataSource dataSource = dataSource();
        PostgresDistributedLockPort holder = new PostgresDistributedLockPort(dataSource);
        PostgresDistributedLockPort other = new PostgresDistributedLockPort(dataSource);
        try {
            holder.tryLock("it:close", Duration.ofSeconds(30)).orElseThrow();
            assertThat(other.tryLock("it:close", Duration.ofSeconds(30))).isEmpty();

            holder.close();
            assertThat(advisorySessions()).isZero();
            assertThat(other.tryLock("it:close", Duration.ofSeconds(30))).isPresent();
        } finally {
            holder.close();
            other.close();
        }
    }

    // ---- 数据库侧核对 ----

    /** 本测试建立、且仍在线的 advisory lock 专用会话数。 */
    private static int advisorySessions() throws SQLException {
        try (Connection connection = dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND pid <> pg_backend_pid()")) {
            statement.setString(1, APPLICATION_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /** 当前 PostgreSQL 里的会话级 advisory lock 数。 */
    private static int advisoryLocks() throws SQLException {
        try (Connection connection = dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
