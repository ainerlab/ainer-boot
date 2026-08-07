package dev.ainer.module.ai.gateway.api;

import dev.ainer.module.ai.gateway.domain.AiInvocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiInvocationResponse(
        UUID id,
        String subjectId,
        String requestId,
        String provider,
        String requestedModel,
        String resolvedModel,
        boolean streaming,
        String status,
        String policyDecision,
        Integer inputTokens,
        Integer outputTokens,
        boolean usageEstimated,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        String currency,
        Long latencyMillis,
        String providerRequestId,
        String errorCode,
        Instant startedAt,
        Instant completedAt) {

    static AiInvocationResponse from(AiInvocation invocation) {
        return new AiInvocationResponse(
                invocation.id(), invocation.subjectId(), invocation.requestId(),
                invocation.provider(), invocation.requestedModel(), invocation.resolvedModel(),
                invocation.streaming(), invocation.status().name(), invocation.policyDecision().name(),
                invocation.inputTokens(), invocation.outputTokens(), invocation.usageEstimated(),
                invocation.estimatedCost(), invocation.actualCost(), invocation.currency(),
                invocation.latencyMillis(), invocation.providerRequestId(), invocation.errorCode(),
                invocation.startedAt(), invocation.completedAt());
    }
}
