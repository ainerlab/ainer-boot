package dev.ainer.module.workspace.workspace.application;

/**
 * Workspace 授权审计中记录的动作类型，覆盖工作空间生命周期、成员治理与审计读取。
 *
 * <p>每次授权检查（无论 ALLOWED 还是 DENIED）都会以其中一个动作值落审计行，
 * 构成低基数的稳定枚举，便于按动作维度聚合分析。
 */
public enum WorkspaceAuthorizationAction {
    WORKSPACE_CREATE,
    WORKSPACE_READ,
    WORKSPACE_PAGE,
    WORKSPACE_RENAME,
    MEMBER_INVITE,
    MEMBERSHIP_ACCEPT,
    MEMBER_ROLE_CHANGE,
    MEMBER_REMOVE,
    OWNERSHIP_TRANSFER,
    AUTHORIZATION_AUDIT_READ
}
