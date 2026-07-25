package dev.ainer.security.error;

import dev.ainer.core.error.ErrorCode;

public enum AinerSecurityErrorCode implements ErrorCode {

    ONLINE_VALIDATION_UNAVAILABLE(
            "AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE",
            "身份状态校验暂时不可用",
            503),
    RECENT_STRONG_AUTHENTICATION_REQUIRED(
            "AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED",
            "需要完成近期强认证后才能执行该操作",
            403);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AinerSecurityErrorCode(String code, String defaultMessage, int httpStatus) {
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
