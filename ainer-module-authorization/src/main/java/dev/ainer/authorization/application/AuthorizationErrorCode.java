package dev.ainer.authorization.application;

import dev.ainer.core.error.ErrorCode;

/**
 * Authorization-module error codes (ADR-0030 S1). Uses the stable {@code AINER.AUTHORIZATION.<ERROR>}
 * convention — no hash allocation or manual numeric fields.
 */
public enum AuthorizationErrorCode implements ErrorCode {
    ROLE_NOT_FOUND("AINER.AUTHORIZATION.ROLE_NOT_FOUND", "角色不存在", 404),
    ROLE_ALREADY_EXISTS("AINER.AUTHORIZATION.ROLE_ALREADY_EXISTS", "角色代码已被使用", 409),
    ROLE_DISABLED("AINER.AUTHORIZATION.ROLE_DISABLED", "角色已禁用", 409),
    ROLE_NOT_SYSTEM("AINER.AUTHORIZATION.ROLE_NOT_SYSTEM", "只有系统角色支持此操作", 422),
    BINDING_NOT_FOUND("AINER.AUTHORIZATION.BINDING_NOT_FOUND", "主体绑定不存在", 404),
    BINDING_ALREADY_REVOKED("AINER.AUTHORIZATION.BINDING_ALREADY_REVOKED", "主体绑定已撤销", 409),
    PERMISSION_NOT_FOUND("AINER.AUTHORIZATION.PERMISSION_NOT_FOUND", "权限不存在于已注册目录", 422),
    PERMISSION_NOT_ASSIGNABLE("AINER.AUTHORIZATION.PERMISSION_NOT_ASSIGNABLE", "权限不在可授予目录中", 422),
    INVALID_SCOPE("AINER.AUTHORIZATION.INVALID_SCOPE", "授权范围不合法", 422),
    SCOPE_NOT_ASSIGNABLE("AINER.AUTHORIZATION.SCOPE_NOT_ASSIGNABLE", "授权范围不可由通用管理接口授予", 422),
    SELF_GRANT_FORBIDDEN("AINER.AUTHORIZATION.SELF_GRANT_FORBIDDEN", "通用管理接口禁止修改自己的授权", 403),
    GRANT_ADMINISTRATION_DENIED(
            "AINER.AUTHORIZATION.GRANT_ADMINISTRATION_DENIED", "当前主体不具备授权管理资格", 403),
    CONCURRENT_MODIFICATION("AINER.AUTHORIZATION.CONCURRENT_MODIFICATION", "授权对象已被并发修改", 409);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AuthorizationErrorCode(String code, String defaultMessage, int httpStatus) {
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
