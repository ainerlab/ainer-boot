package dev.ainer.module.workspace.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceMember(
        TenantId tenantId,
        UUID workspaceId,
        SubjectId subjectId,
        WorkspaceRole role,
        WorkspaceMemberStatus status,
        SubjectId invitedBy,
        Instant createdAt,
        Instant activatedAt,
        Instant updatedAt) {

    public WorkspaceMember {
        Objects.requireNonNull(tenantId, "tenantId");
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
            TenantId tenantId, UUID workspaceId, SubjectId subjectId, Instant now) {
        return new WorkspaceMember(
                tenantId, workspaceId, subjectId, WorkspaceRole.OWNER, WorkspaceMemberStatus.ACTIVE,
                subjectId, now, now, now);
    }

    public static WorkspaceMember invitation(
            TenantId tenantId,
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole role,
            SubjectId invitedBy,
            Instant now) {
        if (!role.canBeAssignedByMemberEndpoint()) {
            throw new IllegalArgumentException("Owner cannot be invited through the member endpoint");
        }
        return new WorkspaceMember(
                tenantId, workspaceId, subjectId, role, WorkspaceMemberStatus.PENDING,
                invitedBy, now, null, now);
    }

    public boolean isActive() {
        return status == WorkspaceMemberStatus.ACTIVE;
    }
}
