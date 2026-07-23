package dev.ainer.module.workspace.workspace.application;

import java.util.List;

public record WorkspaceAuthorizationAuditPage(
        List<WorkspaceAuthorizationAudit> items,
        int page,
        int size,
        long total) {

    public WorkspaceAuthorizationAuditPage {
        items = List.copyOf(items);
    }
}
