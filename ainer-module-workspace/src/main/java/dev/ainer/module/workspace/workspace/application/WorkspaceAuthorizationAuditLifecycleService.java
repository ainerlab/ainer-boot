package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkspaceAuthorizationAuditLifecycleService {

    private final WorkspaceAuthorizationAuditRepository repository;
    private final WorkspaceSecurityOperationAuditRepository operationAuditRepository;
    private final Clock clock;

    public WorkspaceAuthorizationAuditLifecycleService(
            WorkspaceAuthorizationAuditRepository repository,
            WorkspaceSecurityOperationAuditRepository operationAuditRepository,
            Clock clock) {
        this.repository = repository;
        this.operationAuditRepository = operationAuditRepository;
        this.clock = clock;
    }

    @Transactional
    public int archiveBefore(Instant cutoff, int batchSize) {
        Objects.requireNonNull(cutoff, "cutoff");
        if (batchSize < 1 || batchSize > 5000) {
            throw new IllegalArgumentException("Workspace audit archive batch size is invalid");
        }
        return repository.archiveBefore(cutoff, clock.instant(), batchSize);
    }

    @Transactional
    public WorkspaceAuthorizationAuditExportBatch export(
            String exporterServiceId,
            UUID workspaceId,
            WorkspaceAuthorizationAuditCursor cursor,
            int limit) {
        Objects.requireNonNull(exporterServiceId, "exporterServiceId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (limit < 1 || limit > 1000) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_AUDIT_EXPORT_REQUEST);
        }
        List<WorkspaceAuthorizationAudit> rows = repository.exportAfter(
                workspaceId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<WorkspaceAuthorizationAudit> items = hasMore
                ? List.copyOf(rows.subList(0, limit))
                : rows;
        WorkspaceAuthorizationAuditCursor nextCursor = items.isEmpty()
                ? cursor
                : new WorkspaceAuthorizationAuditCursor(
                        items.getLast().occurredAt(), items.getLast().id());
        UUID operationId = UUID.randomUUID();
        operationAuditRepository.insert(new WorkspaceSecurityOperationAudit(
                UUID.randomUUID(), operationId, workspaceId, null,
                "AUTHORIZATION_AUDIT_EXPORT", "EXPORTED", exporterServiceId,
                null, items.size(), clock.instant()));
        return new WorkspaceAuthorizationAuditExportBatch(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public WorkspaceAuthorizationAuditOperationalStatus status(Instant deniedSince) {
        return repository.operationalStatus(Objects.requireNonNull(deniedSince, "deniedSince"));
    }
}
