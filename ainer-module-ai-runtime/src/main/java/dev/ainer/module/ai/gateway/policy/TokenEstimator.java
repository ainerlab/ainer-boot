package dev.ainer.module.ai.gateway.policy;

import dev.ainer.module.ai.gateway.domain.ModelMessage;
import dev.ainer.module.ai.gateway.domain.TokenUsage;

import java.util.List;

/**
 * Token 估算器：按字符数（默认 4 字符/token 加消息开销）估算输入与输出 token，
 * 用于调用前的预算预占。
 */
public final class TokenEstimator {

    private static final int CHARACTERS_PER_TOKEN = 4;
    private static final int MESSAGE_OVERHEAD_TOKENS = 4;

    public int estimateInputTokens(List<ModelMessage> messages) {
        long estimate = 0;
        for (ModelMessage message : messages) {
            int codePoints = message.content().codePointCount(0, message.content().length());
            estimate += Math.max(1, ceilDiv(codePoints, CHARACTERS_PER_TOKEN));
            estimate += MESSAGE_OVERHEAD_TOKENS;
        }
        return Math.toIntExact(Math.min(estimate, Integer.MAX_VALUE));
    }

    public int estimateOutputTokens(String content) {
        int codePoints = content.codePointCount(0, content.length());
        return Math.max(1, ceilDiv(codePoints, CHARACTERS_PER_TOKEN));
    }

    public TokenUsage estimateUsage(List<ModelMessage> messages, String output) {
        return new TokenUsage(estimateInputTokens(messages), estimateOutputTokens(output), true);
    }

    private int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
