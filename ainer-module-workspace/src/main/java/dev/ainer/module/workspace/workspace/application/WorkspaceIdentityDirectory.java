package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.SubjectId;

public interface WorkspaceIdentityDirectory {

    boolean isActiveHumanAccount(SubjectId subjectId);
}
