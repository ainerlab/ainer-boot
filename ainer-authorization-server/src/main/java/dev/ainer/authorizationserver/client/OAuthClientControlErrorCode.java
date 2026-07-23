package dev.ainer.authorizationserver.client;

import dev.ainer.core.error.ErrorCode;

public enum OAuthClientControlErrorCode implements ErrorCode {
    INVALID_REQUEST(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_INVALID_REQUEST",
            "OAuth 服务客户端请求不合法",
            422),
    SCOPE_NOT_ALLOWED(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_SCOPE_NOT_ALLOWED",
            "OAuth 服务客户端包含未获准的权限范围",
            422),
    CLIENT_ALREADY_EXISTS(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_ALREADY_EXISTS",
            "OAuth 服务客户端已经存在",
            409),
    CLIENT_NOT_FOUND(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_NOT_FOUND",
            "OAuth 服务客户端不存在",
            404),
    CLIENT_NOT_ACTIVE(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_NOT_ACTIVE",
            "OAuth 服务客户端不是可操作的活动状态",
            409),
    CLIENT_STATE_CONFLICT(
            "AINER.AUTHORIZATION.OAUTH_CLIENT_STATE_CONFLICT",
            "OAuth 服务客户端状态已发生变化",
            409);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    OAuthClientControlErrorCode(String code, String defaultMessage, int httpStatus) {
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
