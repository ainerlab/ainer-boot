package dev.ainer.module.ai.gateway.domain;

public record TokenUsage(int inputTokens, int outputTokens, boolean estimated) {

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Token usage cannot be negative");
        }
        if ((long) inputTokens + outputTokens > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Total token usage is too large");
        }
    }

    public int totalTokens() {
        return Math.addExact(inputTokens, outputTokens);
    }
}
