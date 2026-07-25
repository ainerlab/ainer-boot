package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.ErrorCode;

public enum PasskeyErrorCode implements ErrorCode {
    INVALID_RECOVERY_REQUEST("AINER.PASSKEY.INVALID_RECOVERY_REQUEST", "Passkey 恢复请求不合法", 422),
    RECOVERY_LOCKED_OUT("AINER.PASSKEY.RECOVERY_LOCKED_OUT", "恢复码尝试次数过多，已临时锁定", 429),
    RECOVERY_CODE_NOT_ACTIVE("AINER.PASSKEY.RECOVERY_CODE_NOT_ACTIVE", "没有可用的恢复码", 409),
    RECOVERY_REQUEST_NOT_FOUND(
            "AINER.PASSKEY.RECOVERY_REQUEST_NOT_FOUND", "Passkey 管理员恢复请求或资源不存在", 404),
    RECOVERY_REQUEST_CONFLICT(
            "AINER.PASSKEY.RECOVERY_REQUEST_CONFLICT", "Passkey 管理员恢复请求状态已变化", 409),
    RECOVERY_APPROVER_MUST_DIFFER(
            "AINER.PASSKEY.RECOVERY_APPROVER_MUST_DIFFER", "恢复批准者必须与申请者不同", 409),
    RECOVERY_REQUEST_EXPIRED(
            "AINER.PASSKEY.RECOVERY_REQUEST_EXPIRED", "Passkey 管理员恢复请求已过期", 409),
    RECOVERY_NOT_REQUIRED(
            "AINER.PASSKEY.RECOVERY_NOT_REQUIRED", "该账号当前没有 ACTIVE Passkey 需要恢复", 409);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    PasskeyErrorCode(String code, String defaultMessage, int httpStatus) {
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
