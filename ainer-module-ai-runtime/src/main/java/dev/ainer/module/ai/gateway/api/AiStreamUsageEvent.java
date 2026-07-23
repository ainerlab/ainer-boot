package dev.ainer.module.ai.gateway.api;

import java.util.UUID;

public record AiStreamUsageEvent(
        UUID invocationId,
        String providerRequestId,
        String model,
        String finishReason,
        TokenUsageResponse usage,
        CostResponse cost,
        long latencyMillis) {
}
