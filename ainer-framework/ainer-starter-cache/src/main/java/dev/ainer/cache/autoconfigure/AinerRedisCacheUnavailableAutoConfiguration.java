package dev.ainer.cache.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * {@code ainer.cache.type=redis} 但 classpath 上没有 Spring Data Redis 时的明确失败。
 *
 * <p>{@code ainer-starter-cache} 的 Redis 依赖是 {@code optional}（ADR-0039：不强制消费者依赖
 * Redis），因此不会传递给应用。若应用声明了 {@code type=redis} 却没有引入 Redis 客户端，
 * 此前的结果是「没有 Redis 缓存自动配置、也没有本地缓存自动配置」——上下文里一个
 * {@code CacheManager} 都没有，直到缓存切面初始化才抛出难以定位的错误。这里改为在装配阶段
 * 直接给出可执行的修复建议。
 */
@AutoConfiguration(after = AinerCacheAutoConfiguration.class)
@Conditional(AinerCacheConditions.OnCacheTypeRedis.class)
@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "ainer.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerRedisCacheUnavailableAutoConfiguration {

    @Bean
    public Object ainerRedisClientRequired() {
        throw new IllegalStateException("""
                ainer.cache.type=redis 需要 classpath 上存在 Spring Data Redis，但当前 classpath 没有 \
                org.springframework.data.redis.core.StringRedisTemplate。ainer-starter-cache 的 Redis 依赖是 \
                optional，不会传递给消费者：请在应用 pom 中显式引入 \
                org.springframework.boot:spring-boot-starter-data-redis，或改回 ainer.cache.type=local \
                使用 Caffeine 本地缓存。""");
    }
}
