package dev.ainer.module.workspace.workspace.application;

import java.util.List;

public record WorkspaceAuthorizationAuditExportBatch(
        List<WorkspaceAuthorizationAudit> items,
        WorkspaceAuthorizationAuditCursor nextCursor,
        boolean hasMore) {

    public WorkspaceAuthorizationAuditExportBatch {
        items = List.copyOf(items);
    }
}
