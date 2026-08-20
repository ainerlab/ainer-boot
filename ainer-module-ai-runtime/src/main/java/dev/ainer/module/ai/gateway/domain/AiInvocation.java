package dev.ainer.module.ai.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次 AI 模型调用的审计聚合根：记录主体、请求、模型、策略决策、prompt 指纹、
 * Token 用量（预估/实际）、费用、耗时、结果状态与供应商请求标识。
 *
 * <p>按治理要求不保存 prompt 与输出正文；prompt 只保留 SHA-256 指纹。
 * 通过工厂方法 {@code started} / {@code rejected} 构造初始与被拒绝状态。
 */
public record AiInvocation(
        UUID id,
        String subjectId,
        String requestId,
        String provider,
        String requestedModel,
        String resolvedModel,
        boolean streaming,
        InvocationStatus status,
        PolicyDecision policyDecision,
        String promptFingerprint,
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

    public AiInvocation {
        Objects.requireNonNull(id, "id");
        subjectId = requireText(subjectId, "subjectId");
        requestId = requireText(requestId, "requestId");
        provider = requireText(provider, "provider");
        requestedModel = requireText(requestedModel, "requestedModel");
        resolvedModel = requireText(resolvedModel, "resolvedModel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyDecision, "policyDecision");
        promptFingerprint = requireText(promptFingerprint, "promptFingerprint");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        currency = requireText(currency, "currency");
        Objects.requireNonNull(startedAt, "startedAt");
        if (estimatedCost.signum() < 0 || actualCost != null && actualCost.signum() < 0) {
            throw new IllegalArgumentException("Invocation cost cannot be negative");
        }
        if (latencyMillis != null && latencyMillis < 0) {
            throw new IllegalArgumentException("Invocation latency cannot be negative");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Invocation completion cannot precede start");
        }
    }

    public static AiInvocation started(
            UUID id,
            String subjectId,
            String requestId,
            String provider,
            String requestedModel,
            String resolvedModel,
            boolean streaming,
            String promptFingerprint,
            CostBreakdown estimatedCost,
            Instant startedAt) {
        return new AiInvocation(
                id, subjectId, requestId, provider, requestedModel, resolvedModel, streaming,
                InvocationStatus.STARTED, PolicyDecision.ALLOWED, promptFingerprint,
                null, null, false, estimatedCost.amount(), null, estimatedCost.currency(),
                null, null, null, startedAt, null);
    }

    public static AiInvocation rejected(
            UUID id,
            String subjectId,
            String requestId,
            String provider,
            String requestedModel,
            String resolvedModel,
            boolean streaming,
            PolicyDecision decision,
            String promptFingerprint,
            CostBreakdown estimatedCost,
            String errorCode,
            Instant now) {
        return new AiInvocation(
                id, subjectId, requestId, provider, requestedModel, resolvedModel, streaming,
                InvocationStatus.REJECTED, decision, promptFingerprint,
                null, null, false, estimatedCost.amount(), null, estimatedCost.currency(),
                0L, null, errorCode, now, now);
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
