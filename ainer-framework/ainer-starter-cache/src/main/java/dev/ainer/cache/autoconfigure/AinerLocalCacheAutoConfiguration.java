package dev.ainer.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * 默认本地缓存装配（ADR-0039）。当 {@code ainer.cache.type} 缺失或为 {@code local} 时激活：
 * Spring Cache 使用 Caffeine，零外部依赖。
 *
 * <p>TTL 与容量来自 {@link AinerCacheProperties.Local}，默认值（30 分钟 / 10000 条）与
 * ADR-0039「不改变现有默认行为」的要求一致。
 *
 * <p>分布式锁不在这里装配：锁的选择由 {@link AinerCacheLockAutoConfiguration} 按
 * {@code ainer.cache.lock.type} 统一决定（本地缓存 + PG advisory lock 是合法组合）。
 */
@AutoConfiguration(after = AinerCacheAutoConfiguration.class)
@EnableConfigurationProperties(AinerCacheProperties.class)
@Conditional(AinerCacheConditions.OnCacheTypeLocal.class)
@ConditionalOnProperty(prefix = "ainer.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerLocalCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(AinerCacheProperties properties) {
        AinerCacheProperties.Local local = properties.local();
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(local.timeToLive())
                .maximumSize(local.maximumSize()));
        return manager;
    }
}
