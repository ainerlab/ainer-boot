package dev.ainer.core.error;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 预期内的业务失败。web 适配器会把它映射为该错误码对应的真实 HTTP 状态码。
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

    public BusinessException(ErrorCode errorCode, @Nullable Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
