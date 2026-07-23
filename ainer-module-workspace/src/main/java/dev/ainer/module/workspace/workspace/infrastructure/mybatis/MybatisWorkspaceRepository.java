package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspacePage;
import dev.ainer.module.workspace.workspace.application.WorkspaceRepository;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceName;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisWorkspaceRepository implements WorkspaceRepository {

    private final WorkspaceMapper mapper;

    public MybatisWorkspaceRepository(WorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(Workspace workspace) {
        try {
            if (mapper.insert(toRow(workspace)) != 1) {
                throw new IllegalStateException("Workspace insert affected an unexpected number of rows");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(WorkspaceErrorCode.ALREADY_EXISTS);
        }
    }

    @Override
    public boolean update(Workspace workspace, long expectedVersion) {
        return mapper.updateName(
                workspace.tenantId().value(),
                workspace.id(),
                workspace.name().value(),
                workspace.updatedAt(),
                expectedVersion) == 1;
    }

    @Override
    public Optional<Workspace> findById(TenantId tenantId, UUID id) {
        return Optional.ofNullable(mapper.selectById(tenantId.value(), id)).map(this::toDomain);
    }

    @Override
    public Optional<Workspace> findByIdForUpdate(TenantId tenantId, UUID id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(tenantId.value(), id)).map(this::toDomain);
    }

    @Override
    public WorkspacePage findPage(
            TenantId tenantId, SubjectId subjectId, int page, int size, long offset) {
        return new WorkspacePage(
                mapper.selectPage(tenantId.value(), subjectId.value(), size, offset)
                        .stream().map(this::toDomain).toList(),
                page,
                size,
                mapper.count(tenantId.value(), subjectId.value()));
    }

    private WorkspaceRow toRow(Workspace workspace) {
        WorkspaceRow row = new WorkspaceRow();
        row.setId(workspace.id());
        row.setTenantId(workspace.tenantId().value());
        row.setName(workspace.name().value());
        row.setVersion(workspace.version());
        row.setCreatedAt(workspace.createdAt());
        row.setUpdatedAt(workspace.updatedAt());
        return row;
    }

    private Workspace toDomain(WorkspaceRow row) {
        return new Workspace(
                row.getId(),
                new TenantId(row.getTenantId()),
                new WorkspaceName(row.getName()),
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
