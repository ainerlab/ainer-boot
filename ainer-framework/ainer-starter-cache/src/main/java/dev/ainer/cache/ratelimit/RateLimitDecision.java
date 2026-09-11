package dev.ainer.cache.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次限流判定的结果：是否放行 + 剩余额度 + 重试等待时间（ADR-0039 §1）。
 *
 * <p>三个字段都是调用方真正需要的运维信息：{@link Outcome#LIMIT_EXCEEDED} 时 {@code retryAfter}
 * 可直接映射到 HTTP {@code Retry-After}；{@link Outcome#BACKEND_UNAVAILABLE} 时它没有可信值，
 * 调用方必须按自己的退避策略处理（见 {@link #retryAfter()}）。
 *
 * @param outcome    判定结果
 * @param remaining  本窗口剩余的可用配额（本次消耗之后）；后端不可用时为 {@code 0} 表示「未知」，
 *                   调用方不得据此判定放行
 * @param retryAfter 建议的重试等待时间；放行时为 {@link Duration#ZERO}
 */
public record RateLimitDecision(Outcome outcome, long remaining, Duration retryAfter) {

    public RateLimitDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(retryAfter, "retryAfter");
    }

    /** 判定结果。 */
    public enum Outcome {

        /** 放行，配额已原子消耗。 */
        ALLOWED,

        /** 窗口内配额不足，请求被拒绝且<strong>未消耗</strong>配额。 */
        LIMIT_EXCEEDED,

        /**
         * 限流后端不可用（连接失败、超时或返回不可解析的结果），按<strong>失败关闭</strong>拒绝。
         * 与 {@link #LIMIT_EXCEEDED} 区分开是为了让运维能区分「业务真的打满了配额」与「限流基础设施在抖动」，
         * 两者的处置动作完全不同。
         */
        BACKEND_UNAVAILABLE
    }

    /** 是否放行。只有 {@link Outcome#ALLOWED} 为 {@code true}。 */
    public boolean allowed() {
        return this.outcome == Outcome.ALLOWED;
    }

    /** 放行，剩余 {@code remaining} 个配额。 */
    public static RateLimitDecision allowed(long remaining) {
        return new RateLimitDecision(Outcome.ALLOWED, Math.max(remaining, 0), Duration.ZERO);
    }

    /**
     * 超限拒绝。
     *
     * @param remaining  本窗口剩余配额（可能是正数：本次申请的 {@code permits} 大于剩余量）
     * @param retryAfter 距本窗口结束的等待时间，恒为正
     */
    public static RateLimitDecision limitExceeded(long remaining, Duration retryAfter) {
        Objects.requireNonNull(retryAfter, "retryAfter");
        return new RateLimitDecision(Outcome.LIMIT_EXCEEDED, Math.max(remaining, 0), retryAfter);
    }

    /**
     * 后端不可用，失败关闭拒绝。
     *
     * <p>{@code retryAfter} 为 {@link Duration#ZERO}：后端故障时没有可信的重试时间，给出一个数字
     * 反而会诱导调用方按它重试。调用方应使用自己的退避策略（或直接把 429/503 交给上游）。
     */
    public static RateLimitDecision backendUnavailable() {
        return new RateLimitDecision(Outcome.BACKEND_UNAVAILABLE, 0L, Duration.ZERO);
    }
}
