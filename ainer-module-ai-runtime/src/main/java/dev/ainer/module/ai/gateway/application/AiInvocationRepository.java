package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.TokenUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiInvocationRepository {

    void lockTenantBudget(String tenantId);

    BigDecimal sumDailyExposure(String tenantId, Instant fromInclusive, Instant toExclusive);

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

    Optional<AiInvocation> findByTenantAndId(String tenantId, UUID id);
}
