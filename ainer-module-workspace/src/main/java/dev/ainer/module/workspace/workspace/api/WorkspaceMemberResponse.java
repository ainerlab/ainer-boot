package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** 成员投影响应：域枚举序列化为稳定 String，避免枚举重命名破坏 JSON 契约。 */
public record WorkspaceMemberResponse(
        UUID workspaceId,
        String subjectId,
        String role,
        String status,
        String invitedBy,
        Instant createdAt,
        @Nullable Instant activatedAt,
        Instant updatedAt) {

    static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.workspaceId(),
                member.subjectId().value(),
                member.role().name(),
                member.status().name(),
                member.invitedBy().value(),
                member.createdAt(),
                member.activatedAt(),
                member.updatedAt());
    }
}
