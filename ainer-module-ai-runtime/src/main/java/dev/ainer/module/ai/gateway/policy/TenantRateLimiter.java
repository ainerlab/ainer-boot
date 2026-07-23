package dev.ainer.module.ai.gateway.policy;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TenantRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int requestsPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public TenantRateLimiter(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    public boolean tryAcquire(String tenantId) {
        long now = clock.millis();
        long bucket = now / WINDOW_MILLIS;
        boolean allowed = windows.compute(tenantId, (ignored, current) -> {
            if (current == null || current.bucket != bucket) {
                return new Window(bucket, 1, true);
            }
            if (current.count >= requestsPerMinute) {
                return new Window(bucket, current.count, false);
            }
            return new Window(bucket, current.count + 1, true);
        }).lastAllowed;
        if ((calls.incrementAndGet() & 1023) == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().bucket < bucket - 1);
        }
        return allowed;
    }

    private record Window(long bucket, int count, boolean lastAllowed) {
    }
}
