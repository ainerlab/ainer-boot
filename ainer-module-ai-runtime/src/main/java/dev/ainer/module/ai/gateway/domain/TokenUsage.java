package dev.ainer.module.ai.gateway.domain;

/**
 * Token 用量：输入/输出 token 数及是否为预估值（供应商未返回 usage 时为 true）。
 */
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
