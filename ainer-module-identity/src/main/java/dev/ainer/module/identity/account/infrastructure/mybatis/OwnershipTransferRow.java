package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class OwnershipTransferRow {

    private UUID id;
    private UUID tenantId;
    private UUID initiatorSubjectId;
    private UUID targetSubjectId;
    private String status;
    private String reasonCode;
    private String requestId;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant executedAt;
    private UUID executedBySubjectId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getInitiatorSubjectId() { return initiatorSubjectId; }
    public void setInitiatorSubjectId(UUID initiatorSubjectId) { this.initiatorSubjectId = initiatorSubjectId; }

    public UUID getTargetSubjectId() { return targetSubjectId; }
    public void setTargetSubjectId(UUID targetSubjectId) { this.targetSubjectId = targetSubjectId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public UUID getExecutedBySubjectId() { return executedBySubjectId; }
    public void setExecutedBySubjectId(UUID executedBySubjectId) { this.executedBySubjectId = executedBySubjectId; }
}
