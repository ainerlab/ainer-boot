package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.application.WorkspacePage;

import java.util.List;

public record WorkspacePageResponse(List<WorkspaceResponse> items, int page, int size, long total) {

    static WorkspacePageResponse from(WorkspacePage workspacePage) {
        return new WorkspacePageResponse(
                workspacePage.items().stream().map(WorkspaceResponse::from).toList(),
                workspacePage.page(),
                workspacePage.size(),
                workspacePage.total());
    }
}
