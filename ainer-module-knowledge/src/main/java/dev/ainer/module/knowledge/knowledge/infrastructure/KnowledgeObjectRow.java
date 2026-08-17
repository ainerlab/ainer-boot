package dev.ainer.module.knowledge.knowledge.infrastructure;
import java.time.Instant;
import java.util.UUID;

/** Row for knowledge module. */
public class KnowledgeObjectRow {

    private UUID id;
    private UUID workspaceId;
    private String kind;
    private String title;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public UUID getWorkspaceId() { return workspaceId; }

    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getKind() { return kind; }

    public void setKind(String kind) { this.kind = kind; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
