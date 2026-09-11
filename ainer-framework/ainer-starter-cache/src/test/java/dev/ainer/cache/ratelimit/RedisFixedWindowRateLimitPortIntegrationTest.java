package dev.ainer.cache.ratelimit;

import io.lettuce.core.ClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

/**
 * Redis 固定窗口限流集成测试（真实 Redis {@code redis:7-alpine}，ADR-0039 §2）。
 *
 * <p>本类要证明的核心命题只有一个：<strong>多实例共享同一份计数，总放行量等于阈值，不放大</strong>。
 * 因此核心用例用「两个独立 {@link RateLimitPort} 实例 + 两条独立 Lettuce 连接 + 400 个并发任务」
 * 来跑——串行调用只能证明计数器存在，证明不了「两个实例同时读到 59 再各自自增」的穿透不存在。
 *
 * <p>窗口序号由注入的 {@link MutableClock} 固定，把「窗口是否稳定」从被测命题里剔除：
 * 生产时钟偏移的影响是另一件事（见 {@link RedisFixedWindowRateLimitPort} javadoc），
 * 不应让核心断言变成「恰好没跨越分钟边界」的概率游戏。
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class RedisFixedWindowRateLimitPortIntegrationTest {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisFixedWindowRateLimitPortIntegrationTest.class);

    private static final String KEY_PREFIX = "ainer:test:ratelimit:";

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** 固定窗口起点（epoch 对齐的整分钟，便于手算窗口序号）。 */
    private static final Instant WINDOW_START = Instant.parse("2026-09-12T10:00:00Z");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    /** 两个实例各持一条独立连接（模拟两个 JVM，而不是共享同一个客户端）。 */
    private LettuceConnectionFactory connectionA;

    private LettuceConnectionFactory connectionB;

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        this.connectionA = newConnectionFactory();
        this.connectionB = newConnectionFactory();
        this.clock = new MutableClock(WINDOW_START);
        // 每个用例用独立 key，但前缀下不许有历史残留（否则计数从上次的值继续）
        StringRedisTemplate redis = new StringRedisTemplate(this.connectionA);
        var stale = redis.keys(KEY_PREFIX + "*");
        if (stale != null && !stale.isEmpty()) {
            redis.delete(stale);
        }
        // 两条连接都预热：并发用例里若有实例在起跑后才握手，竞争会退化成单边碾压，失去交织意义
        new StringRedisTemplate(this.connectionB).hasKey(KEY_PREFIX + "connection-warmup");
    }

    @AfterEach
    void tearDown() {
        this.connectionA.destroy();
        this.connectionB.destroy();
    }

    // ---- 核心：多实例总放行量不放大（并发证明） ----

    @Test
    void concurrentAdmissionsAcrossTwoInstancesNeverExceedTheLimit() throws Exception {
        int limit = 100;
        int attemptsPerInstance = 200;
        String key = "ai:subject:" + UUID.randomUUID();
        RateLimitPort instanceA = instance(this.connectionA);
        RateLimitPort instanceB = instance(this.connectionB);

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger allowedA = new AtomicInteger();
        AtomicInteger allowedB = new AtomicInteger();
        AtomicInteger deniedA = new AtomicInteger();
        AtomicInteger deniedB = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < attemptsPerInstance; i++) {
                tasks.add(executor.submit(() -> attempt(instanceA, key, limit, startGate, allowedA, deniedA)));
                tasks.add(executor.submit(() -> attempt(instanceB, key, limit, startGate, allowedB, deniedB)));
            }
            // 同时放闸，让两个实例的请求真正交织在一起
            startGate.countDown();
            for (Future<?> task : tasks) {
                task.get(60, TimeUnit.SECONDS);
            }
        }

        int totalAllowed = allowedA.get() + allowedB.get();
        int totalDenied = deniedA.get() + deniedB.get();
        // 原始断言：两个实例并发消耗同一 key 时，集群总放行量恰好等于阈值。
        // 计数不共享（每实例独立）时这里会是 200；判断与自增非原子时这里会大于 100。
        assertThat(totalAllowed)
                .as("两实例并发总放行量必须等于阈值（A=%d, B=%d, 拒绝=%d）",
                        allowedA.get(), allowedB.get(), totalDenied)
                .isEqualTo(limit);
        assertThat(totalDenied).isEqualTo(attemptsPerInstance * 2 - limit);

        // 共享计数键的真值：Redis 里只有一个 key，值恰好是阈值
        StringRedisTemplate redis = new StringRedisTemplate(this.connectionA);
        long windowIndex = FixedWindow.index(this.clock.millis(), WINDOW.toMillis());
        String storageKey = FixedWindow.storageKey(KEY_PREFIX, key, windowIndex);
        assertThat(redis.opsForValue().get(storageKey)).isEqualTo(Integer.toString(limit));
        assertThat(redis.keys(KEY_PREFIX + key + ":*")).containsExactly(storageKey);
        // 原始实测数字：并发用例的全部价值就是这个不变量，留在日志里便于复核
        LOGGER.info("[ainer-cache] 两实例并发限流实测：阈值={}，实例A放行={}，实例B放行={}，总放行={}，拒绝={}，"
                        + "Redis 计数键 {}={}",
                limit, allowedA.get(), allowedB.get(), totalAllowed, totalDenied,
                storageKey, redis.opsForValue().get(storageKey));
    }

    private static void attempt(RateLimitPort port, String key, int limit, CountDownLatch startGate,
            AtomicInteger allowed, AtomicInteger denied) {
        try {
            startGate.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        if (port.tryAcquire(key, 1, limit, WINDOW).allowed()) {
            allowed.incrementAndGet();
        } else {
            denied.incrementAndGet();
        }
    }

    @Test
    void quotaIsSharedAcrossInstancesConsumedOneByOne() {
        // 并发用例证明「不放量」，本用例用串行交错证明「真共享」：实例 B 消耗掉的配额会让实例 A 被拒。
        // 两个实例各自独立计数时，最后一个断言会失败（A 仍持有自己那份完整配额）。
        RateLimitPort instanceA = instance(this.connectionA);
        RateLimitPort instanceB = instance(this.connectionB);
        String key = "ai:subject:shared";

        assertThat(instanceA.tryAcquire(key, 1, 2, WINDOW).allowed()).isTrue();
        assertThat(instanceB.tryAcquire(key, 1, 2, WINDOW).allowed()).isTrue();

        RateLimitDecision denied = instanceA.tryAcquire(key, 1, 2, WINDOW);
        assertThat(denied.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(denied.remaining()).isZero();
        assertThat(instanceB.tryAcquire(key, 1, 2, WINDOW).allowed()).isFalse();
    }

    // ---- 窗口推进与 retryAfter ----

    @Test
    void windowAdvanceRestoresQuotaAndRetryAfterShrinksTowardsWindowEnd() {
        RateLimitPort port = instance(this.connectionA);
        String key = "ai:subject:window";

        RateLimitDecision first = port.tryAcquire(key, 1, 2, WINDOW);
        assertThat(first.outcome()).isEqualTo(RateLimitDecision.Outcome.ALLOWED);
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(first.retryAfter()).isZero();
        assertThat(port.tryAcquire(key, 1, 2, WINDOW).remaining()).isZero();

        RateLimitDecision exhausted = port.tryAcquire(key, 1, 2, WINDOW);
        assertThat(exhausted.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(exhausted.remaining()).isZero();
        // 窗口起点调用 → 距窗口结束正好 60 秒
        assertThat(exhausted.retryAfter()).isEqualTo(WINDOW);

        this.clock.advance(Duration.ofSeconds(20));
        RateLimitDecision later = port.tryAcquire(key, 1, 2, WINDOW);
        assertThat(later.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        // 同一窗口内 retryAfter 单调收敛：40 秒 < 60 秒，且仍然为正
        assertThat(later.retryAfter()).isEqualTo(Duration.ofSeconds(40));
        assertThat(later.retryAfter()).isLessThan(exhausted.retryAfter()).isPositive();

        long firstWindowIndex = FixedWindow.index(WINDOW_START.toEpochMilli(), WINDOW.toMillis());
        assertThat(new StringRedisTemplate(this.connectionA)
                .getExpire(FixedWindow.storageKey(KEY_PREFIX, key, firstWindowIndex)))
                .as("旧窗口键的 TTL 必须有上界（不会永久保留计数）")
                .isPositive()
                .isLessThanOrEqualTo(WINDOW.toSeconds());

        // 推进到下一窗口：配额恢复，且落在新的窗口键上
        this.clock.advance(Duration.ofSeconds(40));
        RateLimitDecision nextWindow = port.tryAcquire(key, 1, 2, WINDOW);
        assertThat(nextWindow.outcome()).isEqualTo(RateLimitDecision.Outcome.ALLOWED);
        assertThat(nextWindow.remaining()).isEqualTo(1);
        assertThat(new StringRedisTemplate(this.connectionA)
                .opsForValue()
                .get(FixedWindow.storageKey(
                        KEY_PREFIX, key, FixedWindow.index(this.clock.millis(), WINDOW.toMillis()))))
                .isEqualTo("1");
    }

    // ---- 拒绝不消耗配额、多配额申请原子 ----

    @Test
    void rejectedRequestDoesNotConsumeQuotaAndMultiPermitAcquireIsAtomic() {
        RateLimitPort port = instance(this.connectionA);
        String key = "ai:subject:permits";

        // 申请量大于上限：拒绝且完全不落计数
        RateLimitDecision oversized = port.tryAcquire(key, 5, 3, WINDOW);
        assertThat(oversized.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(oversized.remaining()).isEqualTo(3);
        long windowIndex = FixedWindow.index(this.clock.millis(), WINDOW.toMillis());
        String storageKey = FixedWindow.storageKey(KEY_PREFIX, key, windowIndex);
        StringRedisTemplate redis = new StringRedisTemplate(this.connectionA);
        assertThat(redis.hasKey(storageKey))
                .as("被拒绝的请求不应创建/自增计数键")
                .isFalse();

        assertThat(port.tryAcquire(key, 2, 3, WINDOW)).isEqualTo(RateLimitDecision.allowed(1));
        // 剩余 1 个配额，申请 2 个：拒绝且不消耗（否则并发重试会把窗口提前打满）
        RateLimitDecision partial = port.tryAcquire(key, 2, 3, WINDOW);
        assertThat(partial.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(partial.remaining()).isEqualTo(1);
        assertThat(redis.opsForValue().get(storageKey)).isEqualTo("2");

        assertThat(port.tryAcquire(key, 1, 3, WINDOW)).isEqualTo(RateLimitDecision.allowed(0));
        assertThat(redis.opsForValue().get(storageKey)).isEqualTo("3");
    }

    // ---- key 命名空间与 TTL ----

    @Test
    void storageKeyCarriesPrefixNamespaceAndWindowIndexWithBoundedTtl() {
        this.clock.set(WINDOW_START.plus(Duration.ofSeconds(7)));
        RateLimitPort port = instance(this.connectionA);

        assertThat(port.tryAcquire("ai:subject:abc", 1, 5, WINDOW).allowed()).isTrue();

        long windowIndex = FixedWindow.index(WINDOW_START.toEpochMilli(), WINDOW.toMillis());
        String storageKey = KEY_PREFIX + "ai:subject:abc:" + windowIndex;
        StringRedisTemplate redis = new StringRedisTemplate(this.connectionA);
        assertThat(redis.opsForValue().get(storageKey)).isEqualTo("1");
        // TTL 精确到窗口结束（10:00:07 调用 → 53 秒），因此不会跨窗口累积、也不会永久保留
        assertThat(redis.getExpire(storageKey)).isBetween(1L, WINDOW.toSeconds());
        assertThat(FixedWindow.storageKey(KEY_PREFIX, "ai:subject:abc", windowIndex))
                .isEqualTo(storageKey);
    }

    @Test
    void windowKeyExpiresAtWindowEndWithoutLeaking() {
        // 窗口序号用可控时钟固定（保证只有一个键），TTL 是真实 Redis 过期：证明计数键不会永久保留
        // ——不维护 TTL 的固定窗口实现会让键无限累积。
        this.clock.set(WINDOW_START.plus(Duration.ofSeconds(1)));
        Duration shortWindow = Duration.ofSeconds(3);
        RateLimitPort port = instance(this.connectionA);
        String key = "ai:subject:short-window";

        assertThat(port.tryAcquire(key, 1, 1, shortWindow).allowed()).isTrue();
        assertThat(port.tryAcquire(key, 1, 1, shortWindow).allowed()).isFalse();

        StringRedisTemplate redis = new StringRedisTemplate(this.connectionA);
        String storageKey = FixedWindow.storageKey(
                KEY_PREFIX, key, FixedWindow.index(this.clock.millis(), shortWindow.toMillis()));
        assertThat(redis.opsForValue().get(storageKey)).isEqualTo("1");
        // TTL 精确到窗口结束：10:00:01 调用 + 3 秒窗口 → 还剩 2 秒
        assertThat(redis.getExpire(storageKey)).isBetween(1L, 2L);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> !Boolean.TRUE.equals(redis.hasKey(storageKey)));
        assertThat(redis.keys(KEY_PREFIX + key + ":*")).isEmpty();
    }

    // ---- Redis 不可用：失败关闭 + 节流告警 ----

    @Test
    void backendUnavailableFailsClosedAndThrottlesWarning(CapturedOutput output) {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
        LettuceConnectionFactory factory = failFastConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        try {
            RateLimitPort port = new RedisFixedWindowRateLimitPort(
                    new StringRedisTemplate(factory), KEY_PREFIX, Clock.systemUTC());
            assertThat(port.tryAcquire("ai:subject:outage", 1, 5, WINDOW).allowed())
                    .as("容器在线时正常放行")
                    .isTrue();

            redis.stop();

            for (int attempt = 0; attempt < 5; attempt++) {
                RateLimitDecision decision = port.tryAcquire("ai:subject:outage", 1, 5, WINDOW);
                assertThat(decision.outcome())
                        .as("第 %d 次调用：Redis 不可用必须失败关闭，绝不放行", attempt + 1)
                        .isEqualTo(RateLimitDecision.Outcome.BACKEND_UNAVAILABLE);
                assertThat(decision.allowed()).isFalse();
                assertThat(decision.remaining()).isZero();
                assertThat(decision.retryAfter()).isZero();
            }
        } finally {
            factory.destroy();
            if (redis.isRunning()) {
                redis.stop();
            }
        }

        // 告警按 30 秒节流：5 次失败只留 1 条 WARN，且明说「失败关闭」而不是静默放行
        assertThat(countOf(output.getAll(), "限流后端 Redis 不可用")).isEqualTo(1);
        assertThat(output.getAll())
                .contains("按失败关闭拒绝请求")
                .contains("条同类告警被抑制");
    }

    // ---- 参数校验（编程错误不属于「失败关闭」范围） ----

    @Test
    void invalidArgumentsFailFast() {
        RateLimitPort port = instance(this.connectionA);

        assertThat(catchThrowable(
                () -> port.tryAcquire("  ", 1, 5, WINDOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
        assertThat(catchThrowable(
                () -> port.tryAcquire("k", 0, 5, WINDOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permits");
        assertThat(catchThrowable(
                () -> port.tryAcquire("k", 1, 0, WINDOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThat(catchThrowable(
                () -> port.tryAcquire("k", 1, 5, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    // ---- 夹具 ----

    private RateLimitPort instance(LettuceConnectionFactory connection) {
        return new RedisFixedWindowRateLimitPort(
                new StringRedisTemplate(connection), KEY_PREFIX, this.clock);
    }

    private static LettuceConnectionFactory newConnectionFactory() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    /** 断连立即失败（不缓冲重放），命令超时 2 秒：让「Redis 不可用」以明确的异常快速暴露。 */
    private static LettuceConnectionFactory failFastConnectionFactory(String host, int port) {
        LettuceClientConfiguration configuration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(false)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port), configuration);
        factory.afterPropertiesSet();
        return factory;
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /** 可控时钟（测试内推进窗口；生产用系统 UTC 时钟）。 */
    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }

        @Override
        public long millis() {
            return this.now.toEpochMilli();
        }

        @Override
        public String toString() {
            return "MutableClock{" + this.now + "}";
        }
    }
}
