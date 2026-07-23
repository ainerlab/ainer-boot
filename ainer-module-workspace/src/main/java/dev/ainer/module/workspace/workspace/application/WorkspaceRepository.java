package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    void insert(Workspace workspace);

    boolean update(Workspace workspace, long expectedVersion);

    Optional<Workspace> findById(TenantId tenantId, UUID id);

    Optional<Workspace> findByIdForUpdate(TenantId tenantId, UUID id);

    WorkspacePage findPage(TenantId tenantId, SubjectId subjectId, int page, int size, long offset);
}
