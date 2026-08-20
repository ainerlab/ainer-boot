package dev.ainer.module.ai.gateway.application;

import java.util.Objects;

/**
 * 一次 AI 调用的最小追溯上下文：主体标识（subjectId）与请求标识（requestId），
 * 构造时校验安全字符集与长度。
 */
public record InvocationContext(String subjectId, String requestId) {

    public InvocationContext {
        subjectId = requireIdentifier(subjectId, "subjectId");
        requestId = requireIdentifier(requestId, "requestId");
    }

    private static String requireIdentifier(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > 128 || !value.matches("[A-Za-z0-9._:@/-]+")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
