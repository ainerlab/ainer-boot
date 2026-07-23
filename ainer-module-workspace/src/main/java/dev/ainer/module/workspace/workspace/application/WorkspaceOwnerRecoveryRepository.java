package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceOwnerRecoveryRepository {

    void expireOpenRequests(TenantId tenantId, UUID workspaceId, Instant now);

    void insert(WorkspaceOwnerRecoveryRequest request);

    Optional<WorkspaceOwnerRecoveryRequest> findForUpdate(TenantId tenantId, UUID requestId);

    boolean markExecuted(UUID requestId, String approvedBy, Instant executedAt);
}
