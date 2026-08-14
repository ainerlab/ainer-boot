package dev.ainer.module.notification.notification.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code ainer_notification_audit}. */
public class NotificationAuditRow {
    private UUID id;
    private String operation;
    private UUID templateId;
    private String actorIssuer;
    private String actorType;
    private String actorId;
    private String requestId;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public String getActorIssuer() { return actorIssuer; }
    public void setActorIssuer(String actorIssuer) { this.actorIssuer = actorIssuer; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
