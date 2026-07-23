package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.Workspace;

import java.util.List;

public record WorkspacePage(List<Workspace> items, int page, int size, long total) {

    public WorkspacePage {
        items = List.copyOf(items);
    }
}
