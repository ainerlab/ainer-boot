package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.SubjectId;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    void insert(Workspace workspace);

    boolean update(Workspace workspace, long expectedVersion);

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByIdForUpdate(UUID id);

    WorkspacePage findPage(SubjectId subjectId, int page, int size, long offset);
}
