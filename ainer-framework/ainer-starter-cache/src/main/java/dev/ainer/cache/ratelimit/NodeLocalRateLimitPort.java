package dev.ainer.cache.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内固定窗口限流——无 Redis 时的降级实现（ADR-0039 §1 第三层的降级档）。
 *
 * <p><strong>它不是集群精确的</strong>：计数只存在于当前 JVM 的 {@link ConcurrentHashMap} 里，
 * N 个实例各自持有一份完整配额，集群总放行量是 {@code limit × N}。装配层因此在启动期打 WARN，
 * 并在 {@code AinerCacheCapabilities} 中打印 {@code rateLimit{..., clusterAccurate=false}}；
 * {@link #clusterAccurate()} 恒返回 {@code false}，任何依赖「集群精确配额」的调用方都应据此在
 * 启动期或测试中断言，而不是等到生产上发现阈值被放大。
 *
 * <p>语义与 {@link RedisFixedWindowRateLimitPort} 保持一致（窗口 epoch 对齐、拒绝不消耗配额、
 * 存储 key 布局相同），因此从 node-local 切到 Redis 只是阈值从「每实例」变成「全局」，
 * 不会引入第二套窗口语义。
 *
 * <p>内存占用：每个窗口每个 key 一条记录，窗口推进后旧记录不会再被访问；清理发生在同一 key 的
 * 下次调用或命中上限时（见 {@link #sweep}），因此占用上界与「窗口数 × 活跃 key 数」同阶。
 * 这个端口用于兜底而不是主路径，不引入后台清理线程。
 */
public final class NodeLocalRateLimitPort implements RateLimitPort {

    /** 单 JVM 内保留的窗口计数条目上限，超过后按过期顺序清理。 */
    static final int MAX_TRACKED_WINDOWS = 10_000;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final String keyPrefix;
    private final Clock clock;

    /** 使用系统 UTC 时钟与默认前缀。 */
    public NodeLocalRateLimitPort() {
        this("ainer:ratelimit:", Clock.systemUTC());
    }

    /**
     * @param keyPrefix 基础设施 key 前缀（只影响进程内键的可读性，与 Redis 实现保持同一布局）
     * @param clock     窗口计算时钟（测试可注入可控时钟）
     */
    public NodeLocalRateLimitPort(String keyPrefix, Clock clock) {
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 使用指定时钟与默认前缀。 */
    public NodeLocalRateLimitPort(Clock clock) {
        this("ainer:ratelimit:", clock);
    }

    @Override
    public RateLimitDecision tryAcquire(String key, int permits, int limit, Duration window) {
        FixedWindow.requireValidArguments(key, permits, limit, window);
        long windowMillis = window.toMillis();
        long now = this.clock.millis();
        long windowIndex = FixedWindow.index(now, windowMillis);
        long retryAfterMillis = FixedWindow.endMillis(windowIndex, windowMillis) - now;
        String storageKey = FixedWindow.storageKey(this.keyPrefix, key, windowIndex);
        sweep(storageKey);
        Counter counter = this.counters.computeIfAbsent(storageKey, ignored -> new Counter());
        long remaining;
        synchronized (counter) {
            if (counter.used + permits > limit) {
                return RateLimitDecision.limitExceeded(
                        FixedWindow.remaining(limit, counter.used), Duration.ofMillis(retryAfterMillis));
            }
            counter.used += permits;
            remaining = FixedWindow.remaining(limit, counter.used);
        }
        return RateLimitDecision.allowed(remaining);
    }

    @Override
    public boolean clusterAccurate() {
        return false;
    }

    /** 当前保留的窗口计数条目数（诊断/测试用）。 */
    public int trackedWindowCount() {
        return this.counters.size();
    }

    @Override
    public String toString() {
        return "NodeLocalRateLimitPort{keyPrefix=" + this.keyPrefix + "}";
    }

    /**
     * 兜底清理：条目数超过上限时移除当前窗口之外的记录。只在写入路径上触发，不是后台任务；
     * 被限流入口的 key 基数通常远小于上限，正常路径不会走到这里。
     */
    private void sweep(String currentStorageKey) {
        if (this.counters.size() < MAX_TRACKED_WINDOWS) {
            return;
        }
        this.counters.keySet().removeIf(key -> !key.equals(currentStorageKey));
    }

    /** 单窗口计数（每条记录只被同一窗口的调用方访问）。 */
    private static final class Counter {
        private long used;
    }
}
