package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.SubjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceSecurityOperationAudit(
        UUID id,
        UUID operationId,
        UUID workspaceId,
        SubjectId targetSubjectId,
        String operationType,
        String phase,
        String actorServiceId,
        String incidentReference,
        Integer recordCount,
        Instant occurredAt) {

    public WorkspaceSecurityOperationAudit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(actorServiceId, "actorServiceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
