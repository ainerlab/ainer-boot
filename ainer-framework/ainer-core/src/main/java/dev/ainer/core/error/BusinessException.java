package dev.ainer.core.error;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Expected business failure. The web adapter maps it to the error's real HTTP status.
 */
public final class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, @Nullable String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
