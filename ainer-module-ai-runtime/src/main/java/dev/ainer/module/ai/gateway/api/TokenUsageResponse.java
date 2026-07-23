package dev.ainer.module.ai.gateway.api;

import dev.ainer.module.ai.gateway.domain.TokenUsage;

public record TokenUsageResponse(int inputTokens, int outputTokens, int totalTokens, boolean estimated) {

    static TokenUsageResponse from(TokenUsage usage) {
        return new TokenUsageResponse(
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage.estimated());
    }
}
