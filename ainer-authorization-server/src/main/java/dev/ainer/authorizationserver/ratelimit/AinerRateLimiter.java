package dev.ainer.authorizationserver.ratelimit;

import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可复用的 node-local 固定窗口限速器。见 ADR-0016。
 *
 * <p>窗口大小、每窗口上限与 {@link Clock} 可配置，按任意 key（如客户端 IP）计数。明确为 node-local：
 * 多实例部署时每实例独立计数，总上限约为单实例上限乘以实例数；它是“减速”而非集群级硬上限，
 * 多实例阶段需要替换为共享存储（届时另立 ADR）。惰性清理过期窗口，避免无界增长。
 */
public final class AinerRateLimiter {

    private final Duration window;
    private final int maxRequests;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public AinerRateLimiter(Duration window, int maxRequests, Clock clock) {
        Assert.notNull(window, "window cannot be null");
        Assert.isTrue(!window.isNegative() && !window.isZero(), "window must be positive");
        Assert.isTrue(maxRequests > 0, "maxRequests must be positive");
        Assert.notNull(clock, "clock cannot be null");
        this.window = window;
        this.maxRequests = maxRequests;
        this.clock = clock;
    }

    public AcquireResult tryAcquire(String key) {
        Assert.hasText(key, "key cannot be empty");
        long windowMillis = window.toMillis();
        long now = clock.millis();
        long bucket = now / windowMillis;
        Window updated = windows.compute(key, (ignored, current) -> {
            if (current == null || current.bucket != bucket) {
                return new Window(bucket, 1, true);
            }
            if (current.count >= maxRequests) {
                return new Window(bucket, current.count, false);
            }
            return new Window(bucket, current.count + 1, true);
        });
        if ((calls.incrementAndGet() & 1023) == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().bucket < bucket - 1);
        }
        if (updated.lastAllowed) {
            return new AcquireResult(true, 0L);
        }
        long windowEndMillis = (bucket + 1) * windowMillis;
        long retryAfterSeconds = Math.max(1L, (windowEndMillis - now + 999L) / 1000L);
        return new AcquireResult(false, retryAfterSeconds);
    }

    public record AcquireResult(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long bucket, int count, boolean lastAllowed) {
    }
}
