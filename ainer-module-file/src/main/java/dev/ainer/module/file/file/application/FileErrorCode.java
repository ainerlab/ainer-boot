package dev.ainer.module.file.file.application;

import dev.ainer.core.error.ErrorCode;

/**
 * 文件模块错误码（ADR-0040）。使用稳定的 {@code AINER.FILE.<ERROR>} 约定。
 *
 * <p>HTTP 状态码承载传输层真实语义：超出大小上限返回 {@code 413}，内容类型不在
 * 允许列表返回 {@code 415}。
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
