package dev.ainer.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ainer.cache.lock.DistributedLockPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Default local cache configuration (ADR-0039). Active when {@code ainer.cache.type} is absent or
 * {@code local}. Uses Caffeine for Spring Cache and an in-memory map for the lock port.
 *
 * <p>This is the zero-dependency baseline — no Redis required. Products switch to Redis by setting
 * {@code ainer.cache.type=redis} and providing a Redis connection.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ainer.cache", name = "type", havingValue = "local", matchIfMissing = true)
public class AinerLocalCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000));
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockPort.class)
    public DistributedLockPort localDistributedLockPort() {
        return new LocalDistributedLockPort();
    }

    /**
     * In-memory lock implementation — single-instance only. NOT suitable for multi-instance
     * deployments; use Redis-backed lock for production.
     */
    static final class LocalDistributedLockPort implements DistributedLockPort {
        private final ConcurrentHashMap<String, LockHandle> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<LockHandle> tryLock(String key, Duration ttl) {
            String token = UUID.randomUUID().toString();
            LockHandle handle = new LockHandle(key, token);
            var existing = locks.putIfAbsent(key, handle);
            if (existing != null) {
                return Optional.empty();
            }
            // schedule auto-expiry
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(ttl.toMillis()); } catch (InterruptedException e) { return; }
                locks.remove(key, handle);
            });
            return Optional.of(handle);
        }

        @Override
        public void release(LockHandle handle) {
            locks.remove(handle.key(), handle);
        }
    }
}
