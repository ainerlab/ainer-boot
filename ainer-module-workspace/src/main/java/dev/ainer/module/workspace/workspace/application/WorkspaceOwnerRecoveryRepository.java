package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceOwnerRecoveryRepository {

    void expireOpenRequests(UUID workspaceId, Instant now);

    void insert(WorkspaceOwnerRecoveryRequest request);

    Optional<WorkspaceOwnerRecoveryRequest> findForUpdate(UUID requestId);

    boolean markExecuted(UUID requestId, String approvedBy, Instant executedAt);
}
