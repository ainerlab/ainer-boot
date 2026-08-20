package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次已完成调用的结果投影：调用 ID、模型补全、实际费用与耗时。
 */
public record CompletionResult(UUID invocationId, ModelCompletion completion, CostBreakdown cost, long latencyMillis) {

    public CompletionResult {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(cost, "cost");
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis cannot be negative");
        }
    }
}
