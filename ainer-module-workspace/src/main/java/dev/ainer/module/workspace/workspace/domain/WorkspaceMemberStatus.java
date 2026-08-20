package dev.ainer.module.workspace.workspace.domain;

/**
 * Workspace 成员状态机：邀请接受前为 {@code PENDING}，接受后为 {@code ACTIVE}，
 * 被移除或撤销后为 {@code REVOKED}。只有 {@code ACTIVE} 成员参与资源授权。
 */
public enum WorkspaceMemberStatus {
    PENDING,
    ACTIVE,
    REVOKED
}
