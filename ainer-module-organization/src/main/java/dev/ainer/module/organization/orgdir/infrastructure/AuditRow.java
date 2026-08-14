package dev.ainer.module.organization.orgdir.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row for table ainer table: ainer_org_change_audit. */
public class AuditRow {

    private UUID id;

    private String entityType;

    private UUID entityId;

    private String operation;

    private String actorIssuer;

    private String actorType;

    private String actorId;

    private String requestId;

    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getActorIssuer() {
        return actorIssuer;
    }

    public void setActorIssuer(String actorIssuer) {
        this.actorIssuer = actorIssuer;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

}
