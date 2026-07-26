package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class IdentityMemberAuditRow {

    private UUID id;
    private UUID tenantId;
    private UUID actorSubjectId;
    private UUID targetSubjectId;
    private String operation;
    private String role;
    private String reasonCode;
    private String requestId;
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getActorSubjectId() {
        return actorSubjectId;
    }

    public void setActorSubjectId(UUID actorSubjectId) {
        this.actorSubjectId = actorSubjectId;
    }

    public UUID getTargetSubjectId() {
        return targetSubjectId;
    }

    public void setTargetSubjectId(UUID targetSubjectId) {
        this.targetSubjectId = targetSubjectId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
