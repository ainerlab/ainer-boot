package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * Stable, low-cardinality reason code explaining an {@link AuthorizationDecision} (ADR-0030 §6.1, §security).
 * Reason codes must not leak resource existence or policy internals to anonymous/non-member callers.
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
