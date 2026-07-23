package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAction;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAudit;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationDecision;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceAuthorizationAuditResponse(
        UUID id,
        UUID workspaceId,
        String actorSubjectId,
        String targetSubjectId,
        WorkspaceAuthorizationAction action,
        WorkspaceAuthorizationDecision decision,
        String reasonCode,
        Instant occurredAt) {

    static WorkspaceAuthorizationAuditResponse from(WorkspaceAuthorizationAudit audit) {
        return new WorkspaceAuthorizationAuditResponse(
                audit.id(),
                audit.workspaceId(),
                audit.actorSubjectId(),
                audit.targetSubjectId(),
                audit.action(),
                audit.decision(),
                audit.reasonCode(),
                audit.occurredAt());
    }
}
