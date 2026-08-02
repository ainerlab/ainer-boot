package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Stable, low-cardinality permission code (ADR-0030 §3.1), e.g. {@code platform.metrics.read} or
 * {@code merchant.listing.publish}. Product codes are registered by product consumers; Ainer only owns
 * platform codes.
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
