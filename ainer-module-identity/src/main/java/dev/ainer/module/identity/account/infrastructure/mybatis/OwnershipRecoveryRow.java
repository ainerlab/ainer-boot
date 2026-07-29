package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class OwnershipRecoveryRow {

    private UUID id;
    private UUID tenantId;
    private UUID targetSubjectId;
    private String status;
    private String requesterServiceId;
    private String approverServiceId;
    private String incidentReference;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant executedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getTargetSubjectId() { return targetSubjectId; }
    public void setTargetSubjectId(UUID targetSubjectId) { this.targetSubjectId = targetSubjectId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequesterServiceId() { return requesterServiceId; }
    public void setRequesterServiceId(String requesterServiceId) { this.requesterServiceId = requesterServiceId; }

    public String getApproverServiceId() { return approverServiceId; }
    public void setApproverServiceId(String approverServiceId) { this.approverServiceId = approverServiceId; }

    public String getIncidentReference() { return incidentReference; }
    public void setIncidentReference(String incidentReference) { this.incidentReference = incidentReference; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
