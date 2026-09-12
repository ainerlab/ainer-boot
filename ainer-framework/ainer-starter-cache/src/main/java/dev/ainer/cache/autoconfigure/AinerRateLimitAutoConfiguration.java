package dev.ainer.cache.autoconfigure;

import dev.ainer.cache.ratelimit.NodeLocalRateLimitPort;
import dev.ainer.cache.ratelimit.RateLimitPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 限流实现的兜底选择点（ADR-0039 §1 第三层能力）。实现跟随 {@code ainer.cache.type}：
 *
 * <table>
 *   <tr><th>ainer.cache.type</th><th>RateLimitPort 实现</th><th>集群精确</th></tr>
 *   <tr><td>{@code REDIS}</td><td>{@code RedisFixedWindowRateLimitPort}（由
 *       {@link AinerRedisCacheAutoConfiguration} 装配）</td><td>是</td></tr>
 *   <tr><td>{@code LOCAL}（默认）</td><td>{@link NodeLocalRateLimitPort}</td>
 *       <td><strong>否</strong>：每实例独立计数，多实例总阈值放大 N 倍，启动期 WARN</td></tr>
 * </table>
 *
 * <p>本配置在 {@link AinerRedisCacheAutoConfiguration} 之后运行，用 {@code @ConditionalOnMissingBean}
 * 让位给 Redis 实现——与 {@link AinerCacheLockAutoConfiguration} 同一形态。
 *
 * <p>刻意<strong>不</strong>在 Redis 不可用时于运行期降级到进程内计数：那会把「声明了集群精确配额、
 * 实际每实例独立」的缺陷重新引入（ADR-0039 落地补齐要消除的形态）。降级只发生在<strong>装配期</strong>，
 * 以 WARN + {@code AinerCacheCapabilities.rateLimitClusterAccurate=false} 显式可见；运行期 Redis
 * 抖动一律失败关闭，见 {@link dev.ainer.cache.ratelimit.RedisFixedWindowRateLimitPort}。
 *
 * <p>与锁一致：限流不依赖缓存开关。但 Redis 实现与 {@code RedisCacheManager}/{@code RedisDistributedLockPort}
 * 同处 {@link AinerRedisCacheAutoConfiguration}，因此 {@code ainer.cache.enabled=false} 时 Redis
 * 限流也不会装配——此时会落到本类的进程内实现并 WARN。
 */
@AutoConfiguration(after = AinerRedisCacheAutoConfiguration.class)
@EnableConfigurationProperties(AinerCacheProperties.class)
public class AinerRateLimitAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AinerRateLimitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(RateLimitPort.class)
    public RateLimitPort ainerRateLimitPort(AinerCacheProperties properties) {
        String keyPrefix = properties.rateLimit().keyPrefix();
        if (properties.type() == AinerCacheProperties.CacheType.REDIS) {
            LOGGER.warn("""
                    [ainer-cache] ainer.cache.type=redis 但没有可用的 Redis 限流实现（classpath 缺少 \
                    Spring Data Redis，或 ainer.cache.enabled=false）：限流退化为进程内固定窗口，\
                    clusterAccurate=false，多实例部署下总阈值会被放大到 N 倍。""");
        } else {
            LOGGER.warn("""
                    [ainer-cache] ainer.cache.type=local（默认）：限流为进程内固定窗口，clusterAccurate=false，\
                    多实例部署下总阈值会被放大到 N 倍。需要集群精确配额时配置 ainer.cache.type=redis \
                    并引入 spring-boot-starter-data-redis。""");
        }
        return new NodeLocalRateLimitPort(keyPrefix, Clock.systemUTC());
    }
}
