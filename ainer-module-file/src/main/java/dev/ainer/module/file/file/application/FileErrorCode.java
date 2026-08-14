package dev.ainer.module.file.file.application;

import dev.ainer.core.error.ErrorCode;

/**
 * File-module error codes (ADR-0040). Uses the stable {@code AINER.FILE.<ERROR>} convention.
 *
 * <p>HTTP statuses carry transport truth: {@code 413} for oversize payloads, {@code 415} for
 * content types outside the allow-list.
 */
public enum FileErrorCode implements ErrorCode {
    NOT_FOUND("AINER.FILE.NOT_FOUND", "文件不存在", 404),
    FILE_TOO_LARGE("AINER.FILE.FILE_TOO_LARGE", "文件超过允许的大小上限", 413),
    CONTENT_TYPE_NOT_ALLOWED("AINER.FILE.CONTENT_TYPE_NOT_ALLOWED", "文件类型不在允许列表中", 415),
    EMPTY_FILENAME("AINER.FILE.EMPTY_FILENAME", "文件名不能为空", 422),
    INVALID_PAGE("AINER.FILE.INVALID_PAGE", "分页参数不合法", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    FileErrorCode(String code, String defaultMessage, int httpStatus) {
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
