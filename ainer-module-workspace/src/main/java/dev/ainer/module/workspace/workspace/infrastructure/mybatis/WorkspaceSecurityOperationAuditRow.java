package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceSecurityOperationAuditRow {
    private UUID id;
    private UUID operationId;
    private String tenantId;
    private UUID workspaceId;
    private String targetSubjectId;
    private String operationType;
    private String phase;
    private String actorServiceId;
    private String incidentReference;
    private Integer recordCount;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getTargetSubjectId() { return targetSubjectId; }
    public void setTargetSubjectId(String targetSubjectId) { this.targetSubjectId = targetSubjectId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getActorServiceId() { return actorServiceId; }
    public void setActorServiceId(String actorServiceId) { this.actorServiceId = actorServiceId; }
    public String getIncidentReference() { return incidentReference; }
    public void setIncidentReference(String incidentReference) { this.incidentReference = incidentReference; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
