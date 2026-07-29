package dev.ainer.authorizationserver.client;

import dev.ainer.core.error.ErrorCode;

public enum OAuthBrowserClientControlErrorCode implements ErrorCode {
    CLIENT_ALREADY_EXISTS("AINER.AUTHORIZATION.OAUTH_BROWSER_CLIENT_ALREADY_EXISTS", "浏览器客户端已存在", 409),
    CLIENT_NOT_FOUND("AINER.AUTHORIZATION.OAUTH_BROWSER_CLIENT_NOT_FOUND", "浏览器客户端不存在", 404),
    CLIENT_STATE_CONFLICT("AINER.AUTHORIZATION.OAUTH_BROWSER_CLIENT_STATE_CONFLICT", "浏览器客户端状态不允许执行该操作", 409),
    INVALID_CLIENT_REQUEST("AINER.AUTHORIZATION.OAUTH_BROWSER_CLIENT_INVALID_REQUEST", "浏览器客户端请求参数不合法", 422),
    SCOPE_NOT_ALLOWED("AINER.AUTHORIZATION.OAUTH_BROWSER_CLIENT_SCOPE_NOT_ALLOWED", "请求的 scope 不在允许列表内", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    OAuthBrowserClientControlErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public int httpStatus() { return httpStatus; }
}
