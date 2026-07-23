package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAudit;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAction;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditCursor;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditOperationalStatus;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditPage;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationDecision;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditRepository;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MybatisWorkspaceAuthorizationAuditRepository
        implements WorkspaceAuthorizationAuditRepository {

    private final WorkspaceAuthorizationAuditMapper mapper;

    public MybatisWorkspaceAuthorizationAuditRepository(WorkspaceAuthorizationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(WorkspaceAuthorizationAudit audit) {
        WorkspaceAuthorizationAuditRow row = new WorkspaceAuthorizationAuditRow();
        row.setId(audit.id());
        row.setTenantId(audit.tenantId());
        row.setWorkspaceId(audit.workspaceId());
        row.setActorSubjectId(audit.actorSubjectId());
        row.setTargetSubjectId(audit.targetSubjectId());
        row.setAction(audit.action().name());
        row.setDecision(audit.decision().name());
        row.setReasonCode(audit.reasonCode());
        row.setOccurredAt(audit.occurredAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("Workspace authorization audit insert affected an unexpected number of rows");
        }
    }

    @Override
    public WorkspaceAuthorizationAuditPage findPage(
            TenantId tenantId, UUID workspaceId, int page, int size, long offset) {
        return new WorkspaceAuthorizationAuditPage(
                mapper.selectPage(tenantId.value(), workspaceId, size, offset)
                        .stream().map(this::toDomain).toList(),
                page,
                size,
                mapper.count(tenantId.value(), workspaceId));
    }

    @Override
    public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) {
        return mapper.archiveBefore(cutoff, archivedAt, batchSize);
    }

    @Override
    public List<WorkspaceAuthorizationAudit> exportAfter(
            TenantId tenantId, WorkspaceAuthorizationAuditCursor cursor, int limit) {
        return mapper.exportAfter(
                        tenantId.value(),
                        cursor == null ? null : cursor.occurredAt(),
                        cursor == null ? null : cursor.id(),
                        limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince) {
        WorkspaceAuthorizationAuditOperationalStatusRow row = mapper.selectOperationalStatus(deniedSince);
        return new WorkspaceAuthorizationAuditOperationalStatus(
                row.getHot(), row.getArchived(), row.getDeniedInWindow(),
                row.getOwnerlessWorkspaces(), row.getOldestHotAt());
    }

    private WorkspaceAuthorizationAudit toDomain(WorkspaceAuthorizationAuditRow row) {
        return new WorkspaceAuthorizationAudit(
                row.getId(),
                row.getTenantId(),
                row.getWorkspaceId(),
                row.getActorSubjectId(),
                row.getTargetSubjectId(),
                WorkspaceAuthorizationAction.valueOf(row.getAction()),
                WorkspaceAuthorizationDecision.valueOf(row.getDecision()),
                row.getReasonCode(),
                row.getOccurredAt());
    }
}
