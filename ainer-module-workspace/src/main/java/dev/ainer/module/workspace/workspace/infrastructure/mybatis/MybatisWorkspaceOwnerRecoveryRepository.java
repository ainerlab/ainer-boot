package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryRepository;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryRequest;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisWorkspaceOwnerRecoveryRepository implements WorkspaceOwnerRecoveryRepository {

    private final WorkspaceOwnerRecoveryMapper mapper;

    public MybatisWorkspaceOwnerRecoveryRepository(WorkspaceOwnerRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void expireOpenRequests(TenantId tenantId, UUID workspaceId, Instant now) {
        mapper.expireOpenRequests(tenantId.value(), workspaceId, now);
    }

    @Override
    public void insert(WorkspaceOwnerRecoveryRequest request) {
        WorkspaceOwnerRecoveryRequestRow row = new WorkspaceOwnerRecoveryRequestRow();
        row.setId(request.id());
        row.setTenantId(request.tenantId().value());
        row.setWorkspaceId(request.workspaceId());
        row.setNewOwnerSubjectId(request.newOwnerSubjectId().value());
        row.setRequestedBy(request.requestedBy());
        row.setApprovedBy(request.approvedBy());
        row.setIncidentReference(request.incidentReference());
        row.setStatus(request.status());
        row.setRequestedAt(request.requestedAt());
        row.setExpiresAt(request.expiresAt());
        row.setExecutedAt(request.executedAt());
        try {
            if (mapper.insert(row) != 1) {
                throw new IllegalStateException("Workspace owner recovery request insert failed");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_CONFLICT);
        }
    }

    @Override
    public Optional<WorkspaceOwnerRecoveryRequest> findForUpdate(TenantId tenantId, UUID requestId) {
        return Optional.ofNullable(mapper.selectForUpdate(tenantId.value(), requestId))
                .map(this::toDomain);
    }

    @Override
    public boolean markExecuted(UUID requestId, String approvedBy, Instant executedAt) {
        return mapper.markExecuted(requestId, approvedBy, executedAt) == 1;
    }

    private WorkspaceOwnerRecoveryRequest toDomain(WorkspaceOwnerRecoveryRequestRow row) {
        return new WorkspaceOwnerRecoveryRequest(
                row.getId(), new TenantId(row.getTenantId()), row.getWorkspaceId(),
                new SubjectId(row.getNewOwnerSubjectId()), row.getRequestedBy(), row.getApprovedBy(),
                row.getIncidentReference(), row.getStatus(), row.getRequestedAt(),
                row.getExpiresAt(), row.getExecutedAt());
    }
}
