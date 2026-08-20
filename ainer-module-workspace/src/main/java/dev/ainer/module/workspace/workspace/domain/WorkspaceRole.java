package dev.ainer.module.workspace.workspace.domain;

/**
 * Workspace 内的成员角色层级：OWNER、ADMIN、MEMBER。
 *
 * <p>只有 OWNER/ADMIN 能治理工作空间（管理成员、改名等）；OWNER 不能通过通用成员接口
 * 授予——角色变更与移除端点都拒绝以 OWNER 为目标。
 */
public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageWorkspace() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canBeAssignedByMemberEndpoint() {
        return this != OWNER;
    }
}
