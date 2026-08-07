package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAudit;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceAuthorizationAuditExportItemResponse(
        UUID id,
        UUID workspaceId,
        String actorSubjectId,
        String targetSubjectId,
        String action,
        String decision,
        String reasonCode,
        Instant occurredAt) {

    static WorkspaceAuthorizationAuditExportItemResponse from(WorkspaceAuthorizationAudit audit) {
        return new WorkspaceAuthorizationAuditExportItemResponse(
                audit.id(), audit.workspaceId(), audit.actorSubjectId(),
                audit.targetSubjectId(), audit.action().name(), audit.decision().name(),
                audit.reasonCode(), audit.occurredAt());
    }
}
