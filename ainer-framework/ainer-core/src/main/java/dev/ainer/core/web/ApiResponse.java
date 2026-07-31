package dev.ainer.core.web;

import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Stable response envelope. HTTP status remains authoritative for transport semantics.
 */
public record ApiResponse<T>(String code, String message, @Nullable T data, String requestId, Instant timestamp) {

    public ApiResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(StandardErrorCode.OK.code(), StandardErrorCode.OK.defaultMessage(),
                data, requestId, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, @Nullable String message, String requestId) {
        Objects.requireNonNull(errorCode, "errorCode");
        String resolvedMessage = message == null || message.isBlank() ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(errorCode.code(), resolvedMessage, null, requestId, Instant.now());
    }
}
