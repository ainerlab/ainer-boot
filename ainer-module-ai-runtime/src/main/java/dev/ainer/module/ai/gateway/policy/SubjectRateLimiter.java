package dev.ainer.module.ai.gateway.policy;

import dev.ainer.cache.ratelimit.RateLimitDecision;
import dev.ainer.cache.ratelimit.RateLimitPort;

import java.time.Duration;

/**
 * 主体限流策略：按 subjectId 做固定窗口每分钟请求数限制。
 *
 * <p>计数不再由本类维护，而是委托给 {@link RateLimitPort}（ADR-0039 §1 第三层能力）：
 * {@code ainer.cache.type=redis} 时是 Redis 固定窗口，多实例共享同一份计数，集群总阈值不放大；
 * 无 Redis 时装配层提供进程内实现，此时<strong>每个实例各持一份完整配额</strong>，多实例部署下
 * 总阈值会放大到「{@code requestsPerMinute} × 实例数」——该退化以启动期 WARN 与
 * {@code AinerCacheCapabilities.rateLimitClusterAccurate=false} 显式暴露（ADR-0016 的
 * node-local 限流经 ADR-0039 §5 修订为「默认 node-local，Redis 可用时升级为分布式」）。
 *
 * <p>窗口与 epoch 对齐（固定窗口，见 {@link RateLimitPort} 的语义说明），因此本类的对外语义与
 * 改造前的进程内实现一致：同一分钟内第 {@code requestsPerMinute + 1} 次调用被拒绝，
 * 窗口推进后配额重置。key 命名空间为 {@code ai:subject:<subjectId>}，默认前缀下实际存储键是
 * {@code ainer:ratelimit:ai:subject:<subjectId>:<窗口序号>}。
 *
 * <p>拒绝（含限流后端不可用的失败关闭）都表现为 {@link #tryAcquire} 返回 {@code false}，
 * 网关据此保持既有对外契约：错误码 {@code AINER.AI.RATE_LIMITED} + 审计
 * {@code REJECTED_RATE_LIMIT} 不变；「真的超限」与「限流后端抖动」的区分由
 * {@link RateLimitPort} 的判定结果和它自己的 WARN 日志承担，这里不再重复打日志（避免按请求量刷屏）。
 */
public class SubjectRateLimiter {

    /** 每分钟固定窗口。 */
    static final Duration WINDOW = Duration.ofMinutes(1);

    /** 限流 key 命名空间（最终存储键的前缀由 {@code ainer.cache.rate-limit.key-prefix} 补全）。 */
    static final String KEY_NAMESPACE = "ai:subject:";

    private final int requestsPerMinute;
    private final RateLimitPort rateLimitPort;

    /**
     * @param requestsPerMinute 每个 subject 每分钟允许的请求数
     * @param rateLimitPort     限流端口（Redis 固定窗口或进程内降级实现）
     */
    public SubjectRateLimiter(int requestsPerMinute, RateLimitPort rateLimitPort) {
        this.requestsPerMinute = requestsPerMinute;
        this.rateLimitPort = rateLimitPort;
    }

    /** 该限流是否在多实例部署下集群精确（透传端口能力，便于断言与运维探针）。 */
    public boolean clusterAccurate() {
        return this.rateLimitPort.clusterAccurate();
    }

    /**
     * 尝试为 subjectId 消耗一次配额。
     *
     * @return 放行返回 {@code true}；超限或限流后端不可用（失败关闭）返回 {@code false}
     */
    public boolean tryAcquire(String subjectId) {
        RateLimitDecision decision = this.rateLimitPort.tryAcquire(
                KEY_NAMESPACE + subjectId, 1, this.requestsPerMinute, WINDOW);
        return decision.allowed();
    }
}
