package dev.ainer.module.workspace.workspace.application;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface WorkspaceAuthorizationAuditRepository {

    void insert(WorkspaceAuthorizationAudit audit);

    WorkspaceAuthorizationAuditPage findPage(
            UUID workspaceId, int page, int size, long offset);

    int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize);

    List<WorkspaceAuthorizationAudit> exportAfter(
            UUID workspaceId, WorkspaceAuthorizationAuditCursor cursor, int limit);

    WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince);
}
