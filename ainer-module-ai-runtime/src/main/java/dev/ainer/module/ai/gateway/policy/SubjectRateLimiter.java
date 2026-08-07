package dev.ainer.module.ai.gateway.policy;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SubjectRateLimiter {

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SubjectRateLimiter(int requestsPerMinute, Clock clock) {
        this.limit = requestsPerMinute;
        this.clock = clock;
    }

    public boolean tryAcquire(String subjectId) {
        Instant now = clock.instant();
        long bucket = now.getEpochSecond() / 60;
        Window window = windows.computeIfAbsent(subjectId, ignored -> new Window(bucket));
        synchronized (window) {
            if (window.bucket != bucket) {
                window.bucket = bucket;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        private long bucket;
        private int count;

        private Window(long bucket) {
            this.bucket = bucket;
        }
    }
}
