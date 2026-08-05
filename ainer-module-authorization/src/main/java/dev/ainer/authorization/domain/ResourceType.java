package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Stable resource type identifier (ADR-0030 §3.1), e.g. {@code tenant}, {@code merchant.listing},
 * {@code consumer.order}. Resource types and their relationships are owned by product/domain modules.
 */
public record ResourceType(String value) {

    private static final java.util.regex.Pattern SAFE_TYPE =
            java.util.regex.Pattern.compile("[a-z][a-z0-9._-]{0,127}");

    public ResourceType {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (!SAFE_TYPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid resource type: " + value);
        }
        value = normalized;
    }
}
