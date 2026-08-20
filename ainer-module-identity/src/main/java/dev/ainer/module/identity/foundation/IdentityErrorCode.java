package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.ErrorCode;

/** Account 与 principal foundation 拥有的稳定错误码。 */
public enum IdentityErrorCode implements ErrorCode {
    HUMAN_ACCOUNT_NOT_FOUND("AINER.IDENTITY.HUMAN_ACCOUNT_NOT_FOUND", "人类安全账号不存在", 404),
    HUMAN_ACCOUNT_NOT_ACTIVE("AINER.IDENTITY.HUMAN_ACCOUNT_NOT_ACTIVE", "人类安全账号不可认证", 409),
    LOGIN_IDENTITY_ALREADY_EXISTS("AINER.IDENTITY.LOGIN_IDENTITY_ALREADY_EXISTS", "登录标识已经被绑定", 409),
    SERVICE_PRINCIPAL_NOT_FOUND("AINER.IDENTITY.SERVICE_PRINCIPAL_NOT_FOUND", "服务主体不存在", 404),
    SERVICE_PRINCIPAL_NOT_ACTIVE("AINER.IDENTITY.SERVICE_PRINCIPAL_NOT_ACTIVE", "服务主体不可认证", 409),
    OAUTH_CLIENT_BINDING_NOT_FOUND("AINER.IDENTITY.OAUTH_CLIENT_BINDING_NOT_FOUND", "OAuth 客户端绑定不存在", 404),
    OAUTH_CLIENT_BINDING_ALREADY_EXISTS(
            "AINER.IDENTITY.OAUTH_CLIENT_BINDING_ALREADY_EXISTS", "OAuth 客户端已经绑定活动凭据", 409),
    CREDENTIAL_NOT_FOUND("AINER.IDENTITY.CREDENTIAL_NOT_FOUND", "身份凭据不存在", 404),
    CREDENTIAL_REVOKED("AINER.IDENTITY.CREDENTIAL_REVOKED", "身份凭据已被撤销", 409),
    INVALID_CREDENTIAL("AINER.IDENTITY.INVALID_CREDENTIAL", "身份凭据校验失败", 401),
    PROFILE_NOT_FOUND("AINER.IDENTITY.PROFILE_NOT_FOUND", "身份资料不存在", 404);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    IdentityErrorCode(String code, String defaultMessage, int httpStatus) {
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
