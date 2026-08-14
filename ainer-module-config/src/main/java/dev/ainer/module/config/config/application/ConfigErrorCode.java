package dev.ainer.module.config.config.application;

import dev.ainer.core.error.ErrorCode;

/**
 * Config-module error codes (ADR-0040). Stable {@code AINER.CONFIG.<ERROR>} strings.
 */
public enum ConfigErrorCode implements ErrorCode {
    PLAINTEXT_ON_SECRET_KEY("AINER.CONFIG.PLAINTEXT_ON_SECRET_KEY", "已存在的 secret 键不能设置明文值", 409),
    INVALID_PAGE("AINER.CONFIG.INVALID_PAGE", "分页参数不合法", 422),
    INVALID_REQUEST("AINER.CONFIG.INVALID_REQUEST", "请求参数不合法", 400);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ConfigErrorCode(String code, String defaultMessage, int httpStatus) {
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
