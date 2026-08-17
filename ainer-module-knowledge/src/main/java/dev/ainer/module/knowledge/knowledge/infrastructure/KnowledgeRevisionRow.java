package dev.ainer.module.knowledge.knowledge.infrastructure;
import java.time.Instant;
import java.util.UUID;

/** Row for knowledge module. */
public class KnowledgeRevisionRow {

    private UUID id;
    private UUID objectId;
    private Long revisionNumber;
    private String payloadMarkdown;
    private String status;
    private String createdByIssuer;
    private String createdByType;
    private String createdById;
    private Instant createdAt;
    private Instant publishedAt;

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public UUID getObjectId() { return objectId; }

    public void setObjectId(UUID objectId) { this.objectId = objectId; }

    public Long getRevisionNumber() { return revisionNumber; }

    public void setRevisionNumber(Long revisionNumber) { this.revisionNumber = revisionNumber; }

    public String getPayloadMarkdown() { return payloadMarkdown; }

    public void setPayloadMarkdown(String payloadMarkdown) { this.payloadMarkdown = payloadMarkdown; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getCreatedByIssuer() { return createdByIssuer; }

    public void setCreatedByIssuer(String createdByIssuer) { this.createdByIssuer = createdByIssuer; }

    public String getCreatedByType() { return createdByType; }

    public void setCreatedByType(String createdByType) { this.createdByType = createdByType; }

    public String getCreatedById() { return createdById; }

    public void setCreatedById(String createdById) { this.createdById = createdById; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }

    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

}
