package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceOwnerRecoveryRequestRow {

    private UUID id;
    private UUID workspaceId;
    private String newOwnerSubjectId;
    private String requestedBy;
    private String approvedBy;
    private String incidentReference;
    private String status;
    private Instant requestedAt;
    private Instant expiresAt;
    private Instant executedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getNewOwnerSubjectId() { return newOwnerSubjectId; }
    public void setNewOwnerSubjectId(String newOwnerSubjectId) { this.newOwnerSubjectId = newOwnerSubjectId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getIncidentReference() { return incidentReference; }
    public void setIncidentReference(String incidentReference) { this.incidentReference = incidentReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
