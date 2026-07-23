package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        String tenantId,
        UUID workspaceId,
        String subjectId,
        WorkspaceRole role,
        WorkspaceMemberStatus status,
        String invitedBy,
        Instant createdAt,
        Instant activatedAt,
        Instant updatedAt) {

    static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.tenantId().value(),
                member.workspaceId(),
                member.subjectId().value(),
                member.role(),
                member.status(),
                member.invitedBy().value(),
                member.createdAt(),
                member.activatedAt(),
                member.updatedAt());
    }
}
