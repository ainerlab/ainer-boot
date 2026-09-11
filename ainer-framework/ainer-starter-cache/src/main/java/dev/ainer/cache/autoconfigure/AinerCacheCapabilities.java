package dev.ainer.cache.autoconfigure;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * 缓存与分布式协调的「实际生效」快照（ADR-0039 落地补齐）。启动期由
 * {@link AinerCacheCapabilitiesAutoConfiguration} 装配并打印，用于回答一个此前无法回答的问题：
 * <strong>声明了什么，实际生效的又是什么</strong>。
 *
 * <p>背景：在补齐之前，{@code ainer.cache.type=redis} 不会产生任何 {@code CacheManager}，
 * 无 Redis 时 {@code DistributedLockPort} 会静默退化为进程内实现——两者都不会在启动日志里留下痕迹。
 * 第三层能力（限流）落地后同样纳入本报告：{@code RateLimitPort} 一旦退化为进程内固定窗口，
 * 集群总阈值就会放大到「配置值 × 实例数」，必须和锁一样在启动期可见。
 *
 * @param declaredCacheType        配置声明的缓存后端类型
 * @param cacheManagerClass        实际生效的 {@code CacheManager} 实现类名；缓存关闭或没有
 *                                 {@code CacheManager} bean 时为 {@code null}
 * @param cacheTimeToLive          实际生效的缓存 TTL；无 {@code CacheManager} 时为 {@code null}
 * @param declaredLockType         配置声明的锁策略
 * @param lockImplementationClass  实际生效的 {@code DistributedLockPort} 实现类名
 * @param multiInstanceSafe        锁在多实例部署下是否真实互斥（进程内锁为 {@code false}）
 * @param rateLimitImplementationClass 实际生效的 {@code RateLimitPort} 实现类名；未装配时为 {@code null}
 * @param rateLimitClusterAccurate 限流在多实例部署下是否给出集群精确配额（进程内实现为 {@code false}）
 */
public record AinerCacheCapabilities(
        AinerCacheProperties.CacheType declaredCacheType,
        @Nullable String cacheManagerClass,
        @Nullable Duration cacheTimeToLive,
        AinerCacheProperties.LockType declaredLockType,
        @Nullable String lockImplementationClass,
        boolean multiInstanceSafe,
        @Nullable String rateLimitImplementationClass,
        boolean rateLimitClusterAccurate) {

    /** 供启动日志使用的一行摘要：声明值 → 生效值。 */
    public String describe() {
        String cache = this.cacheManagerClass == null
                ? "无 CacheManager（缓存未启用）"
                : this.cacheManagerClass + " (TTL=" + this.cacheTimeToLive + ")";
        return "cache{declared=" + this.declaredCacheType + " → effective=" + cache + "}"
                + " lock{declared=" + this.declaredLockType + " → effective=" + this.lockImplementationClass
                + ", multiInstanceSafe=" + this.multiInstanceSafe + "}"
                + " rateLimit{declared=" + this.declaredCacheType
                + "（实现跟随 ainer.cache.type） → effective=" + describeRateLimit()
                + ", clusterAccurate=" + this.rateLimitClusterAccurate + "}";
    }

    private String describeRateLimit() {
        return this.rateLimitImplementationClass == null
                ? "(未装配 RateLimitPort)"
                : this.rateLimitImplementationClass;
    }
}
