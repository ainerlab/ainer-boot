package dev.ainer.core.error;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates globally stable error codes before an application starts serving traffic.
 */
public final class ErrorCodeRegistry {

    private final Map<String, ErrorCode> codes = new LinkedHashMap<>();

    public ErrorCodeRegistry register(Collection<? extends ErrorCode> errorCodes) {
        Objects.requireNonNull(errorCodes, "errorCodes").forEach(this::register);
        return this;
    }

    public ErrorCodeRegistry register(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode");
        ErrorCode existing = codes.putIfAbsent(errorCode.code(), errorCode);
        if (existing != null) {
            throw new IllegalStateException("Duplicate error code '%s': %s and %s".formatted(
                    errorCode.code(), existing, errorCode));
        }
        return this;
    }

    public Map<String, ErrorCode> snapshot() {
        return Map.copyOf(codes);
    }
}
