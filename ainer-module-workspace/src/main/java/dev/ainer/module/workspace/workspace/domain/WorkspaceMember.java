package dev.ainer.module.workspace.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Workspace 成员记录，绑定主体、角色与成员状态，是资源授权的成员关系事实来源。
 *
 * <p>关键不变量：邀请在受邀主体接受前只能是 {@code PENDING}（此时不允许有激活时间）；
 * 只有 {@code ACTIVE} 成员才参与资源授权；OWNER 必然是 ACTIVE 且不能通过成员邀请接口
 * 产生——OWNER 只能由创建时的首任所有者或专用转移/恢复流程产生。
 */
public record WorkspaceMember(
        UUID workspaceId,
        SubjectId subjectId,
        WorkspaceRole role,
        WorkspaceMemberStatus status,
        SubjectId invitedBy,
        Instant createdAt,
        Instant activatedAt,
        Instant updatedAt) {

    public WorkspaceMember {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(invitedBy, "invitedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Workspace member update time cannot precede creation time");
        }
        if (status == WorkspaceMemberStatus.ACTIVE && activatedAt == null) {
            throw new IllegalArgumentException("Active workspace member requires activation time");
        }
        if (status == WorkspaceMemberStatus.PENDING && activatedAt != null) {
            throw new IllegalArgumentException("Pending workspace member cannot have activation time");
        }
        if (role == WorkspaceRole.OWNER && status == WorkspaceMemberStatus.PENDING) {
            throw new IllegalArgumentException("Workspace owner cannot be pending");
        }
    }

    public static WorkspaceMember owner(
            UUID workspaceId, SubjectId subjectId, Instant now) {
        return new WorkspaceMember(
                workspaceId, subjectId, WorkspaceRole.OWNER, WorkspaceMemberStatus.ACTIVE,
                subjectId, now, now, now);
    }

    public static WorkspaceMember invitation(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole role,
            SubjectId invitedBy,
            Instant now) {
        if (!role.canBeAssignedByMemberEndpoint()) {
            throw new IllegalArgumentException("Owner cannot be invited through the member endpoint");
        }
        return new WorkspaceMember(
                workspaceId, subjectId, role, WorkspaceMemberStatus.PENDING,
                invitedBy, now, null, now);
    }

    public boolean isActive() {
        return status == WorkspaceMemberStatus.ACTIVE;
    }
}
