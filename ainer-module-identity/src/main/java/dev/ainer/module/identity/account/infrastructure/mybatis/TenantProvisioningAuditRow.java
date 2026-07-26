package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class TenantProvisioningAuditRow {

    private UUID id;
    private UUID operationId;
    private UUID tenantId;
    private UUID targetSubjectId;
    private String phase;
    private String actorType;
    private String actorId;
    private String requestId;
    private String changeReference;
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getTargetSubjectId() {
        return targetSubjectId;
    }

    public void setTargetSubjectId(UUID targetSubjectId) {
        this.targetSubjectId = targetSubjectId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getChangeReference() {
        return changeReference;
    }

    public void setChangeReference(String changeReference) {
        this.changeReference = changeReference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
