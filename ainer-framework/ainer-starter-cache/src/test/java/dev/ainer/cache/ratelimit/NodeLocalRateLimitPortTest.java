package dev.ainer.cache.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * node-local 限流降级实现的单元测试（无容器）：语义必须与 Redis 实现一致，唯一差别是
 * {@link RateLimitPort#clusterAccurate()} 为 {@code false}——这个差别是装配层启动期 WARN 的依据，
 * 因此在这里显式断言。
 */
class NodeLocalRateLimitPortTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final Instant START = Instant.parse("2026-09-12T10:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    private final RateLimitPort port = new NodeLocalRateLimitPort(
            "ainer:ratelimit:", this.clock);

    @Test
    void isExplicitlyNotClusterAccurate() {
        assertThat(this.port.clusterAccurate())
                .as("进程内计数在多实例下总阈值放大 N 倍，必须自报 false")
                .isFalse();
    }

    @Test
    void allowsUpToLimitPerEpochAlignedWindowAndRestoresAfterwards() {
        assertThat(this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW))
                .isEqualTo(RateLimitDecision.allowed(1));
        assertThat(this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW))
                .isEqualTo(RateLimitDecision.allowed(0));

        RateLimitDecision exhausted = this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW);
        assertThat(exhausted.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(exhausted.retryAfter()).isEqualTo(WINDOW);

        // 不同 key 独立计数
        assertThat(this.port.tryAcquire("ai:subject:b", 1, 2, WINDOW).allowed()).isTrue();

        // 推进到下一窗口：配额恢复
        this.clock.advance(Duration.ofSeconds(60));
        assertThat(this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW)).isEqualTo(RateLimitDecision.allowed(1));
    }

    @Test
    void rejectedRequestDoesNotConsumeQuotaAndRetryAfterShrinks() {
        assertThat(this.port.tryAcquire("k", 5, 3, WINDOW))
                .isEqualTo(RateLimitDecision.limitExceeded(3, WINDOW));

        assertThat(this.port.tryAcquire("k", 3, 3, WINDOW)).isEqualTo(RateLimitDecision.allowed(0));
        RateLimitDecision partial = this.port.tryAcquire("k", 1, 3, WINDOW);
        assertThat(partial.outcome()).isEqualTo(RateLimitDecision.Outcome.LIMIT_EXCEEDED);
        assertThat(partial.remaining()).isZero();

        this.clock.advance(Duration.ofSeconds(50));
        RateLimitDecision later = this.port.tryAcquire("k", 1, 3, WINDOW);
        assertThat(later.retryAfter()).isEqualTo(Duration.ofSeconds(10));
        assertThat(later.retryAfter()).isLessThan(partial.retryAfter());
    }

    @Test
    void storageKeyLayoutMatchesTheRedisImplementation() {
        this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW);

        long windowIndex = FixedWindow.index(START.toEpochMilli(), WINDOW.toMillis());
        assertThat(FixedWindow.storageKey("ainer:ratelimit:", "ai:subject:a", windowIndex))
                .isEqualTo("ainer:ratelimit:ai:subject:a:" + windowIndex);
        // 每个窗口一条记录；窗口推进后是新记录（旧记录不再被访问）
        assertThat(((NodeLocalRateLimitPort) this.port).trackedWindowCount()).isEqualTo(1);
        this.clock.advance(Duration.ofSeconds(60));
        this.port.tryAcquire("ai:subject:a", 1, 2, WINDOW);
        assertThat(((NodeLocalRateLimitPort) this.port).trackedWindowCount()).isEqualTo(2);
    }

    @Test
    void invalidArgumentsFailFast() {
        assertThat(catchThrowable(() -> this.port.tryAcquire(null, 1, 2, WINDOW)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> this.port.tryAcquire("k", 1, 2, Duration.ofMillis(-1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    /** 可控时钟。 */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
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
    }
}
