package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;

public record AddWorkspaceMemberCommand(String subjectId, WorkspaceRole role) {
}
