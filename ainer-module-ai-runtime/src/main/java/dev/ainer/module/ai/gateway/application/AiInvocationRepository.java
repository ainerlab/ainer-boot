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

    Optional<AiInvocation> findBySubjectAndId(String subjectId, UUID id);
}
