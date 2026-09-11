package dev.ainer.cache.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存与分布式协调配置（ADR-0039）。所有键都在 {@code ainer.cache} 前缀下，默认值保持
 * ADR-0039「运维与迁移」第 2 条要求的历史行为不变：{@code type=LOCAL}（Caffeine）、
 * 30 分钟 TTL、10000 条上限；锁默认 {@code AUTO}，按可用基础设施自动选择实现。
 *
 * <p>本类采用构造器绑定（Spring Boot 4 单构造器自动生效），因此非法值会在启动期
 * 绑定失败而不是被静默忽略。
 *
 * @param enabled 是否启用 Spring Cache 注解驱动（{@code false} 时不注册
 *                {@code @EnableCaching} 切面，{@code @Cacheable} 直接落到方法体）
 * @param type    缓存后端类型；缺省 {@link CacheType#LOCAL}
 * @param local   本地 Caffeine 后端参数
 * @param redis   Redis/Valkey 后端参数
 * @param lock    分布式锁选择策略
 */
@ConfigurationProperties(prefix = "ainer.cache")
public record AinerCacheProperties(
        Boolean enabled,
        CacheType type,
        Local local,
        Redis redis,
        Lock lock) {

    public AinerCacheProperties {
        enabled = enabled == null || enabled;
        type = type == null ? CacheType.LOCAL : type;
        local = local == null ? new Local(null, null) : local;
        redis = redis == null ? new Redis(null, null) : redis;
        lock = lock == null ? new Lock(null) : lock;
    }

    /** 缓存后端类型。{@code LOCAL} 是 ADR-0039 约定的默认值。 */
    public enum CacheType {

        /** Caffeine 进程内缓存（单实例/开发环境），零外部依赖。 */
        LOCAL,

        /** Redis/Valkey 分布式缓存，需 Redis 连接与 jackson-databind。 */
        REDIS
    }

    /** 分布式锁实现选择策略。 */
    public enum LockType {

        /** 自动：有 Redis 缓存后端时用 Redis 锁；否则有 DataSource 时用 PG advisory lock；否则进程内锁 + WARN。 */
        AUTO,

        /** 强制 PostgreSQL 会话级 advisory lock；上下文无 DataSource 时启动失败。 */
        POSTGRES,

        /** 强制进程内锁（仅单实例有效，启动期 WARN）。 */
        LOCAL
    }

    /**
     * 本地 Caffeine 后端参数。
     *
     * @param timeToLive  写入后过期时间，默认 {@code PT30M}（保持 ADR-0039 之前的现状）
     * @param maximumSize 单缓存最大条目数，默认 {@code 10000}
     */
    public record Local(Duration timeToLive, Integer maximumSize) {

        public Local {
            timeToLive = timeToLive == null ? Duration.ofMinutes(30) : timeToLive;
            maximumSize = maximumSize == null ? 10_000 : maximumSize;
            if (timeToLive.isNegative() || timeToLive.isZero()) {
                throw new IllegalArgumentException(
                        "ainer.cache.local.time-to-live 必须为正数，当前为 " + timeToLive);
            }
            if (maximumSize <= 0) {
                throw new IllegalArgumentException(
                        "ainer.cache.local.maximum-size 必须为正数，当前为 " + maximumSize);
            }
        }
    }

    /**
     * Redis/Valkey 后端参数。
     *
     * @param timeToLive 缓存条目 TTL，默认 {@code PT30M}（与本地默认保持一致）
     * @param keyPrefix  缓存 key 前缀，默认 {@code ainer:cache:}；用于多应用共享同一
     *                   Redis 实例时隔离命名空间
     */
    public record Redis(Duration timeToLive, String keyPrefix) {

        public Redis {
            timeToLive = timeToLive == null ? Duration.ofMinutes(30) : timeToLive;
            keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "ainer:cache:" : keyPrefix;
            if (timeToLive.isNegative() || timeToLive.isZero()) {
                throw new IllegalArgumentException(
                        "ainer.cache.redis.time-to-live 必须为正数，当前为 " + timeToLive);
            }
        }
    }

    /**
     * 分布式锁选择参数。
     *
     * @param type 锁实现策略，默认 {@link LockType#AUTO}
     */
    public record Lock(LockType type) {

        public Lock {
            type = type == null ? LockType.AUTO : type;
        }
    }
}
