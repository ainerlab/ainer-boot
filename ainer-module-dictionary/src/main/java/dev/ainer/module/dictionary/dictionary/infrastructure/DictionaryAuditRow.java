package dev.ainer.module.dictionary.dictionary.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code ainer_dictionary_audit}. */
public class DictionaryAuditRow {
    private UUID id;
    private String operation;
    private String targetKind;
    private UUID targetId;
    private String actorIssuer;
    private String actorType;
    private String actorId;
    private String requestId;
    private String detail;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetKind() { return targetKind; }
    public void setTargetKind(String targetKind) { this.targetKind = targetKind; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public String getActorIssuer() { return actorIssuer; }
    public void setActorIssuer(String actorIssuer) { this.actorIssuer = actorIssuer; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
