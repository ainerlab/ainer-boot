package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditPage;

import java.util.List;

public record WorkspaceAuthorizationAuditPageResponse(
        List<WorkspaceAuthorizationAuditResponse> items,
        int page,
        int size,
        long total) {

    static WorkspaceAuthorizationAuditPageResponse from(WorkspaceAuthorizationAuditPage auditPage) {
        return new WorkspaceAuthorizationAuditPageResponse(
                auditPage.items().stream().map(WorkspaceAuthorizationAuditResponse::from).toList(),
                auditPage.page(),
                auditPage.size(),
                auditPage.total());
    }
}
