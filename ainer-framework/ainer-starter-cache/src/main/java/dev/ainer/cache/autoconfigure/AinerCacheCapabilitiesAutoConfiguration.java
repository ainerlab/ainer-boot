package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.lock.DistributedLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;

/**
 * 启动期暴露「声明了什么 vs 实际生效什么」（ADR-0039 落地补齐）。
 *
 * <p>补齐之前有两处静默：{@code ainer.cache.type=redis} 不会产生任何 {@code CacheManager}；
 * 无 Redis 时分布式锁会退化成进程内实现。两者都不在启动日志留下痕迹，运维只能靠读代码推断。
 * 本配置把实际生效的缓存后端类名、TTL、锁实现类名与「锁是否跨实例有效」固化成
 * {@link AinerCacheCapabilities} bean，并在启动期打印；测试与运维探针都可以直接断言它。
 */
@AutoConfiguration(after = {
        AinerLocalCacheAutoConfiguration.class,
        AinerRedisCacheAutoConfiguration.class,
        AinerCacheLockAutoConfiguration.class
})
@EnableConfigurationProperties(AinerCacheProperties.class)
public class AinerCacheCapabilitiesAutoConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AinerCacheCapabilitiesAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AinerCacheCapabilities ainerCacheCapabilities(
            AinerCacheProperties properties,
            ObjectProvider<CacheManager> cacheManagers,
            ObjectProvider<DistributedLockPort> lockPorts) {
        List<CacheManager> caches = cacheManagers.orderedStream().toList();
        List<DistributedLockPort> locks = lockPorts.orderedStream().toList();
        CacheManager cacheManager = caches.size() == 1 ? caches.get(0) : null;
        DistributedLockPort lockPort = locks.size() == 1 ? locks.get(0) : null;
        AinerCacheCapabilities capabilities = new AinerCacheCapabilities(
                properties.type(),
                cacheManager == null ? null : cacheManager.getClass().getName(),
                cacheManager == null ? null : declaredCacheTimeToLive(properties),
                properties.lock().type(),
                describeLock(lockPort, locks.size()),
                lockPort != null && lockPort.multiInstanceSafe());
        LOGGER.info("[ainer-cache] 缓存与分布式协调实际生效情况：{}", capabilities.describe());
        if (!capabilities.multiInstanceSafe()) {
            LOGGER.warn("[ainer-cache] 当前锁实现不具备跨实例互斥能力（multiInstanceSafe=false）：{}",
                    capabilities.describe());
        }
        if (cacheManager == null) {
            LOGGER.info("[ainer-cache] 未装配 CacheManager：ainer.cache.enabled={}，"
                    + "@Cacheable/@CacheEvict 不会生效", properties.enabled());
        }
        return capabilities;
    }

    private static Duration declaredCacheTimeToLive(AinerCacheProperties properties) {
        return switch (properties.type()) {
            case LOCAL -> properties.local().timeToLive();
            case REDIS -> properties.redis().timeToLive();
        };
    }

    private static String describeLock(DistributedLockPort lockPort, int beanCount) {
        if (lockPort != null) {
            return lockPort.getClass().getName();
        }
        return beanCount == 0
                ? "(未装配 DistributedLockPort)"
                : "(多个 DistributedLockPort bean: " + beanCount + ")";
    }
}
