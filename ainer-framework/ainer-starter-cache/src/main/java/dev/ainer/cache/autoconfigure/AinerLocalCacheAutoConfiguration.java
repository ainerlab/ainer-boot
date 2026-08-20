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
 * 默认本地缓存装配（ADR-0039）。当 {@code ainer.cache.type} 缺失或为 {@code local} 时激活：
 * Spring Cache 使用 Caffeine，锁端口使用进程内 Map。
 *
 * <p>这是零依赖基线——不需要 Redis。产品通过设置 {@code ainer.cache.type=redis}
 * 并提供 Redis 连接切换到 Redis。
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
     * 进程内锁实现——仅限单实例。不适合多实例部署；生产环境请使用 Redis 锁。
     */
    static final class LocalDistributedLockPort implements DistributedLockPort {
        private final ConcurrentHashMap<String, LockHandle> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<LockHandle> tryLock(String key, Duration ttl) {
            String token = dev.ainer.core.uuid.Uuidv7.generate().toString();
            LockHandle handle = new LockHandle(key, token);
            var existing = locks.putIfAbsent(key, handle);
            if (existing != null) {
                return Optional.empty();
            }
            // 安排到期自动释放
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
