package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 稳定的资源类型标识（ADR-0030 §3.1），例如 {@code workspace}、{@code merchant.listing}、
 * {@code consumer.order}。资源类型及其关系由产品/领域模块拥有。
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
