package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.ErrorCode;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class WorkspaceAuthorizationAuditService {

    private final WorkspaceAuthorizationAuditRepository repository;
    private final Clock clock;

    public WorkspaceAuthorizationAuditService(
            WorkspaceAuthorizationAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AuthenticatedActor actor,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action,
            WorkspaceAuthorizationDecision decision,
            ErrorCode reason) {
        repository.insert(new WorkspaceAuthorizationAudit(
                UUID.randomUUID(),
                actor.tenantId(),
                workspaceId,
                actor.subjectId(),
                targetSubjectId,
                action,
                decision,
                reason.code(),
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public WorkspaceAuthorizationAuditPage findPage(
            TenantId tenantId, UUID workspaceId, int page, int size, long offset) {
        return repository.findPage(tenantId, workspaceId, page, size, offset);
    }
}
