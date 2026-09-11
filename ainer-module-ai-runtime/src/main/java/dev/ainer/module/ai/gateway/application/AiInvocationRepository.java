package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.TokenUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 调用审计的持久化端口：预算锁定与当日费用汇总、审计行插入与终态回写。
 * 主体只能查询自己的调用记录（{@code findBySubjectAndId}）。
 */
public interface AiInvocationRepository {

    void lockSubjectBudget(String subjectId);

    BigDecimal sumDailyExposure(String subjectId, Instant fromInclusive, Instant toExclusive);

    void insert(AiInvocation invocation);

    boolean markSucceeded(
            UUID id,
            String resolvedModel,
            String providerRequestId,
            TokenUsage usage,
            CostBreakdown actualCost,
            long latencyMillis,
            Instant completedAt);

    boolean markFailed(UUID id, String errorCode, long latencyMillis, Instant completedAt);

    /**
     * 超时/中断失败的终态回写：状态置 FAILED 并把 {@code actual_cost} 置 0，释放这次调用对当日
     * 预算的预占（{@code estimated_cost} 保留在审计行里，只影响费用暴露口径，不丢失审计信息）。
     *
     * <p>与 {@link #markFailed} 的区别是刻意设计的：普通供应商失败仍按预估值占用预算（既有口径，
     * 避免失败重试绕过上限）；而超时/自愈这类「结果未知且不会自己回到终态」的行如果继续占用，
     * 会让该 subject 的当日预算永久被锁死到 UTC 跨日。
     */
    boolean markFailedReleasingReservation(UUID id, String errorCode, long latencyMillis, Instant completedAt);

    /**
     * 把超过阈值仍停在 STARTED 的调用批量推进到 FAILED 并释放预算预占，返回实际处理行数。
     *
     * <p>幂等且并发安全：条件 UPDATE（{@code status = 'STARTED'}）是 CAS，候选行用
     * {@code FOR UPDATE SKIP LOCKED} 领取，因此多个实例同时扫不会重复处理同一行。
     */
    int healStuckStarted(Instant startedBefore, int limit, String errorCode, Instant healedAt);

    /** 仍停在 STARTED 且早于阈值的行数（自愈积压，用于告警）。 */
    long countStuckStarted(Instant startedBefore);

    /** 仍停在 STARTED 的最早 started_at；没有任何中间态时返回 {@code null}。 */
    Instant oldestStartedAt();

    Optional<AiInvocation> findBySubjectAndId(String subjectId, UUID id);
}
