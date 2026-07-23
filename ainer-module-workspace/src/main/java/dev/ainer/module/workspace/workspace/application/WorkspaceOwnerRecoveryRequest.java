package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceOwnerRecoveryRequest(
        UUID id,
        TenantId tenantId,
        UUID workspaceId,
        SubjectId newOwnerSubjectId,
        String requestedBy,
        String approvedBy,
        String incidentReference,
        String status,
        Instant requestedAt,
        Instant expiresAt,
        Instant executedAt) {

    public WorkspaceOwnerRecoveryRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(newOwnerSubjectId, "newOwnerSubjectId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(incidentReference, "incidentReference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
