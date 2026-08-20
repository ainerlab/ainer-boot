package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.ErrorCode;

/**
 * Workspace 模块的错误码集合，统一采用 {@code AINER.WORKSPACE.<ERROR>} 稳定字符串约定。
 *
 * <p>每个错误码绑定真实的 HTTP status（404/403/409/422/503 等），由框架错误装配转换为
 * 传输层语义；不使用 hash 分配或手工数字段位。错误消息不携带资源细节，避免向非成员
 * 泄露 Workspace 存在性。
 */
public enum WorkspaceErrorCode implements ErrorCode {
    NOT_FOUND("AINER.WORKSPACE.NOT_FOUND", "工作空间不存在", 404),
    INVALID_NAME("AINER.WORKSPACE.INVALID_NAME", "工作空间名称不合法", 422),
    INVALID_SUBJECT("AINER.WORKSPACE.INVALID_SUBJECT", "成员主体标识不合法", 422),
    INVALID_PAGE("AINER.WORKSPACE.INVALID_PAGE", "分页参数不合法", 400),
    ROLE_NOT_ASSIGNABLE("AINER.WORKSPACE.ROLE_NOT_ASSIGNABLE", "该工作空间角色不能通过成员接口授予", 422),
    ACCESS_DENIED("AINER.WORKSPACE.ACCESS_DENIED", "无权操作该工作空间", 403),
    INVITATION_NOT_FOUND("AINER.WORKSPACE.INVITATION_NOT_FOUND", "工作空间邀请不存在", 404),
    IDENTITY_DIRECTORY_UNAVAILABLE(
            "AINER.WORKSPACE.IDENTITY_DIRECTORY_UNAVAILABLE", "身份目录暂时不可用", 503),
    MEMBER_NOT_ACTIVE("AINER.WORKSPACE.MEMBER_NOT_ACTIVE", "工作空间成员尚未激活", 409),
    MEMBER_UPDATE_CONFLICT("AINER.WORKSPACE.MEMBER_UPDATE_CONFLICT", "成员状态已发生变化", 409),
    ALREADY_EXISTS("AINER.WORKSPACE.ALREADY_EXISTS", "工作空间已经存在", 409),
    MEMBER_ALREADY_EXISTS("AINER.WORKSPACE.MEMBER_ALREADY_EXISTS", "成员已经存在", 409),
    CONCURRENT_MODIFICATION("AINER.WORKSPACE.CONCURRENT_MODIFICATION", "工作空间已被并发修改", 409),
    INVALID_OWNER_RECOVERY_REQUEST(
            "AINER.WORKSPACE.INVALID_OWNER_RECOVERY_REQUEST", "工作空间所有者恢复请求不合法", 422),
    OWNER_RECOVERY_NOT_FOUND(
            "AINER.WORKSPACE.OWNER_RECOVERY_NOT_FOUND", "工作空间所有者恢复请求或资源不存在", 404),
    OWNER_RECOVERY_NOT_REQUIRED(
            "AINER.WORKSPACE.OWNER_RECOVERY_NOT_REQUIRED", "工作空间当前不需要所有者恢复", 409),
    OWNER_RECOVERY_TARGET_NOT_ACTIVE(
            "AINER.WORKSPACE.OWNER_RECOVERY_TARGET_NOT_ACTIVE", "新所有者目标不是有效成员", 409),
    OWNER_RECOVERY_CONFLICT(
            "AINER.WORKSPACE.OWNER_RECOVERY_CONFLICT", "工作空间所有者恢复状态已发生变化", 409),
    OWNER_RECOVERY_APPROVER_MUST_DIFFER(
            "AINER.WORKSPACE.OWNER_RECOVERY_APPROVER_MUST_DIFFER", "所有者恢复批准者必须与申请者不同", 409),
    OWNER_RECOVERY_EXPIRED(
            "AINER.WORKSPACE.OWNER_RECOVERY_EXPIRED", "工作空间所有者恢复请求已过期", 409),
    INVALID_AUDIT_EXPORT_REQUEST(
            "AINER.WORKSPACE.INVALID_AUDIT_EXPORT_REQUEST", "工作空间授权审计导出请求不合法", 400);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    WorkspaceErrorCode(String code, String defaultMessage, int httpStatus) {
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
