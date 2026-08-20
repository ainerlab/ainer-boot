package dev.ainer.module.notification.notification.application;

import dev.ainer.core.error.ErrorCode;

/**
 * 通知模块错误码（ADR-0040）。稳定的 {@code AINER.NOTIFICATION.<ERROR>} 字符串。
 */
public enum NotificationErrorCode implements ErrorCode {
    TEMPLATE_NOT_FOUND("AINER.NOTIFICATION.TEMPLATE_NOT_FOUND", "通知模板不存在", 404),
    TEMPLATE_ALREADY_EXISTS("AINER.NOTIFICATION.TEMPLATE_ALREADY_EXISTS", "同编码的启用模板已存在", 409),
    CHANNEL_MISMATCH("AINER.NOTIFICATION.CHANNEL_MISMATCH", "通知渠道与模板渠道不一致", 422),
    INVALID_PAGE("AINER.NOTIFICATION.INVALID_PAGE", "分页参数不合法", 422),
    INVALID_REQUEST("AINER.NOTIFICATION.INVALID_REQUEST", "请求参数不合法", 400),
    CONCURRENT_MODIFICATION("AINER.NOTIFICATION.CONCURRENT_MODIFICATION", "通知模板已被并发修改", 409);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    NotificationErrorCode(String code, String defaultMessage, int httpStatus) {
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
