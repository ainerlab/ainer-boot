package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.ErrorCode;

public enum IdentityErrorCode implements ErrorCode {
    INVALID_PROVISIONING_REQUEST("AINER.IDENTITY.INVALID_PROVISIONING_REQUEST", "身份初始化参数不合法", 422),
    INVALID_IDENTITY_REFERENCE("AINER.IDENTITY.INVALID_IDENTITY_REFERENCE", "身份引用不合法", 422),
    INVALID_DIRECTORY_QUERY("AINER.IDENTITY.INVALID_DIRECTORY_QUERY", "身份目录查询参数不合法", 422),
    DIRECTORY_MEMBER_NOT_FOUND("AINER.IDENTITY.DIRECTORY_MEMBER_NOT_FOUND", "身份目录成员不存在", 404),
    ALREADY_EXISTS("AINER.IDENTITY.ALREADY_EXISTS", "租户代码或用户名已经存在", 409),
    ACCOUNT_NOT_FOUND("AINER.IDENTITY.ACCOUNT_NOT_FOUND", "身份账号不存在", 404),
    MEMBERSHIP_NOT_FOUND("AINER.IDENTITY.MEMBERSHIP_NOT_FOUND", "租户成员关系不存在", 404),
    OWNER_REVOCATION_REQUIRES_TRANSFER(
            "AINER.IDENTITY.OWNER_REVOCATION_REQUIRES_TRANSFER", "撤销租户所有者前必须先转移所有权", 409),
    ACCESS_STATE_CONFLICT("AINER.IDENTITY.ACCESS_STATE_CONFLICT", "身份访问状态已发生变化", 409),
    ACCESS_EVENT_LEASE_LOST(
            "AINER.IDENTITY.ACCESS_EVENT_LEASE_LOST", "身份访问事件租约已经失效", 409),
    INVALID_RECOVERY_REQUEST(
            "AINER.IDENTITY.INVALID_RECOVERY_REQUEST", "身份恢复请求不合法", 422),
    ACCESS_EVENT_NOT_EXHAUSTED(
            "AINER.IDENTITY.ACCESS_EVENT_NOT_EXHAUSTED", "身份访问事件不是可重放的耗尽状态", 409),
    REPLAY_REQUEST_NOT_FOUND(
            "AINER.IDENTITY.REPLAY_REQUEST_NOT_FOUND", "身份访问事件重放请求不存在", 404),
    REPLAY_REQUEST_CONFLICT(
            "AINER.IDENTITY.REPLAY_REQUEST_CONFLICT", "身份访问事件重放请求状态已变化", 409),
    REPLAY_APPROVER_MUST_DIFFER(
            "AINER.IDENTITY.REPLAY_APPROVER_MUST_DIFFER", "重放批准者必须与申请者不同", 409),
    REPLAY_REQUEST_EXPIRED(
            "AINER.IDENTITY.REPLAY_REQUEST_EXPIRED", "身份访问事件重放请求已过期", 409);

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
