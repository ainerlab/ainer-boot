package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceAuthorizationAudit(
        UUID id,
        String tenantId,
        UUID workspaceId,
        String actorSubjectId,
        String targetSubjectId,
        WorkspaceAuthorizationAction action,
        WorkspaceAuthorizationDecision decision,
        String reasonCode,
        Instant occurredAt) {

    public WorkspaceAuthorizationAudit {
        Objects.requireNonNull(id, "id");
        tenantId = requireText(tenantId, "tenantId", 128);
        actorSubjectId = requireText(actorSubjectId, "actorSubjectId", 128);
        if (targetSubjectId != null) {
            targetSubjectId = requireText(targetSubjectId, "targetSubjectId", 128);
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(decision, "decision");
        reasonCode = requireText(reasonCode, "reasonCode", 96);
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String name, int maxLength) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
