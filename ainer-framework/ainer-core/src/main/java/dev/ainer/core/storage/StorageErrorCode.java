package dev.ainer.core.storage;

import dev.ainer.core.error.ErrorCode;

/**
 * {@link FileStoragePort} 操作的错误码，与 {@link dev.ainer.core.error.BusinessException} 配合使用。
 */
public enum StorageErrorCode implements ErrorCode {
    STORE_FAILED("AINER.STORAGE.STORE_FAILED", "Failed to store file", 500),
    RESOLVE_FAILED("AINER.STORAGE.RESOLVE_FAILED", "Failed to resolve file", 500),
    DELETE_FAILED("AINER.STORAGE.DELETE_FAILED", "Failed to delete file", 500),
    INVALID_KEY("AINER.STORAGE.INVALID_KEY", "Invalid storage key", 400),
    INVALID_NAMESPACE("AINER.STORAGE.INVALID_NAMESPACE", "Invalid storage namespace", 400);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    StorageErrorCode(String code, String defaultMessage, int httpStatus) {
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
}
