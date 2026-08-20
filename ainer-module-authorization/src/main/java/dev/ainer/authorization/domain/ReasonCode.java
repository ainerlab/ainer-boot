package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 解释 {@link AuthorizationDecision} 的稳定、低基数 reason code（ADR-0030 §6.1、§security）。
 * reason code 不得向匿名/非成员调用方泄露资源存在性或策略内部细节。
 */
public record ReasonCode(String value) {

    private static final java.util.regex.Pattern SAFE_REASON =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9._]{0,127}");

    public ReasonCode {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (!SAFE_REASON.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid reason code: " + value);
        }
        value = normalized;
    }
}
