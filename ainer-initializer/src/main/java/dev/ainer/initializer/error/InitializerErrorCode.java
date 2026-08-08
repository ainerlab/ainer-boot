package dev.ainer.initializer.error;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;

/**
 * Error codes owned by the initializer. Manifest and generation failures are expected input
 * errors reported to the CLI user; they never expose stack traces or file contents.
 */
public enum InitializerErrorCode implements ErrorCode {

    INVALID_MANIFEST("AINER.INITIALIZER.INVALID_MANIFEST", "Manifest 不符合 v1 契约", 422),
    UNSUPPORTED_TARGET("AINER.INITIALIZER.UNSUPPORTED_TARGET", "目标目录不允许写入或为空", 409),
    ILLEGAL_STATE("AINER.INITIALIZER.ILLEGAL_STATE", "Initializer 内部状态不合法", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    InitializerErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Fails fast with an initializer error. Prefer this over raw runtime exceptions so the CLI
     * can print a stable error code and message without exposing internals.
     */
    public static BusinessException exception(InitializerErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}