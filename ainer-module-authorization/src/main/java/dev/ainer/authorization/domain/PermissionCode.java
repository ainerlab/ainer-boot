package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 稳定、低基数的权限 code（ADR-0030 §3.1），例如 {@code platform.metrics.read} 或
 * {@code merchant.listing.publish}。产品 code 由产品消费者注册；Ainer 只拥有平台 code。
 */
public record PermissionCode(String value) {

    private static final java.util.regex.Pattern SAFE_CODE =
            java.util.regex.Pattern.compile("[a-z][a-z0-9._-]{0,127}");

    public PermissionCode {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (!SAFE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid permission code: " + value);
        }
        value = normalized;
    }
}
