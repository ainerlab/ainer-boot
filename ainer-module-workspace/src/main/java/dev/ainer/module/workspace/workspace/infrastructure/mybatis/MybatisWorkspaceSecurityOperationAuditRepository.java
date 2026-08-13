package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.application.WorkspaceSecurityOperationAudit;
import dev.ainer.module.workspace.workspace.application.WorkspaceSecurityOperationAuditRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisWorkspaceSecurityOperationAuditRepository
        implements WorkspaceSecurityOperationAuditRepository {

    private final WorkspaceSecurityOperationAuditMapper mapper;

    public MybatisWorkspaceSecurityOperationAuditRepository(WorkspaceSecurityOperationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(WorkspaceSecurityOperationAudit audit) {
        WorkspaceSecurityOperationAuditRow row = new WorkspaceSecurityOperationAuditRow();
        row.setId(audit.id());
        row.setOperationId(audit.operationId());
        row.setWorkspaceId(audit.workspaceId());
        row.setTargetSubjectId(audit.targetSubjectId() == null ? null : audit.targetSubjectId().value());
        row.setOperationType(audit.operationType());
        row.setPhase(audit.phase());
        row.setActorServiceId(audit.actorServiceId());
        row.setIncidentReference(audit.incidentReference());
        row.setRecordCount(audit.recordCount());
        row.setOccurredAt(audit.occurredAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("Workspace security operation audit insert failed");
        }
    }
}
