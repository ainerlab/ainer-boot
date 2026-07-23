package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.TenantId;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface WorkspaceAuthorizationAuditRepository {

    void insert(WorkspaceAuthorizationAudit audit);

    WorkspaceAuthorizationAuditPage findPage(
            TenantId tenantId, UUID workspaceId, int page, int size, long offset);

    int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize);

    List<WorkspaceAuthorizationAudit> exportAfter(
            TenantId tenantId, WorkspaceAuthorizationAuditCursor cursor, int limit);

    WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince);
}
