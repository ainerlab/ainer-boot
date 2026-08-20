package dev.ainer.module.file.file.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** {@code ainer_file_audit} 的行映射。 */
public class FileAuditRow {
    private UUID id;
    private UUID fileId;
    private String operation;
    private String namespace;
    private String actorIssuer;
    private String actorType;
    private String actorId;
    private String requestId;
    private Instant occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
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
