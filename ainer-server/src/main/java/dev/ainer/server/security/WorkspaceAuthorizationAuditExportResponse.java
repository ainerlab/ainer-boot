package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditExportBatch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkspaceAuthorizationAuditExportResponse(
        List<WorkspaceAuthorizationAuditExportItemResponse> items,
        Instant nextOccurredAt,
        UUID nextId,
        boolean hasMore) {

    static WorkspaceAuthorizationAuditExportResponse from(WorkspaceAuthorizationAuditExportBatch batch) {
        return new WorkspaceAuthorizationAuditExportResponse(
                batch.items().stream().map(WorkspaceAuthorizationAuditExportItemResponse::from).toList(),
                batch.nextCursor() == null ? null : batch.nextCursor().occurredAt(),
                batch.nextCursor() == null ? null : batch.nextCursor().id(),
                batch.hasMore());
    }
}
