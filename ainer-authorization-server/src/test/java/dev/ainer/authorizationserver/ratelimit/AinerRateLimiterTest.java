package dev.ainer.authorizationserver.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerRateLimiterTest {

    @Test
    void allowsUntilMaxThenRejectsUntilWindowResets() {
        MutableClock clock = new MutableClock();
        AinerRateLimiter limiter = new AinerRateLimiter(Duration.ofSeconds(60), 3, clock);

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        AinerRateLimiter.AcquireResult rejected = limiter.tryAcquire("1.2.3.4");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isPositive();

        // 不同 key 独立计数
        assertThat(limiter.tryAcquire("9.9.9.9").allowed()).isTrue();

        // 推进到下一个窗口后恢复
        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
    }

    @Test
    void retryAfterRoundsUpToRemainingWindowSeconds() {
        MutableClock clock = new MutableClock();
        AinerRateLimiter limiter = new AinerRateLimiter(Duration.ofSeconds(60), 1, clock);

        assertThat(limiter.tryAcquire("ip").allowed()).isTrue();
        clock.advance(Duration.ofSeconds(20));
        AinerRateLimiter.AcquireResult rejected = limiter.tryAcquire("ip");
        assertThat(rejected.allowed()).isFalse();
        // 剩余约 40 秒，向上取整
        assertThat(rejected.retryAfterSeconds()).isBetween(30L, 40L);
    }

    @Test
    void rejectsInvalidConfiguration() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> new AinerRateLimiter(Duration.ZERO, 1, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AinerRateLimiter(Duration.ofSeconds(1), 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(0);

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }
    }
}
