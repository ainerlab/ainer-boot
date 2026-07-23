package dev.ainer.module.ai.gateway.api;

import dev.ainer.module.ai.gateway.application.CompletionResult;

import java.util.UUID;

public record ChatCompletionResponse(
        UUID invocationId,
        String providerRequestId,
        String model,
        String content,
        String finishReason,
        TokenUsageResponse usage,
        CostResponse cost,
        long latencyMillis) {

    static ChatCompletionResponse from(CompletionResult result) {
        return new ChatCompletionResponse(
                result.invocationId(),
                result.completion().providerRequestId(),
                result.completion().model(),
                result.completion().content(),
                result.completion().finishReason(),
                TokenUsageResponse.from(result.completion().usage()),
                CostResponse.from(result.cost()),
                result.latencyMillis());
    }
}
