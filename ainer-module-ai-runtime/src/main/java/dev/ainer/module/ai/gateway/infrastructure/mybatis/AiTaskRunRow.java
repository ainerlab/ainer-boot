package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class AiTaskRunRow {
    private UUID id;
    private UUID taskId;
    private UUID contextSnapshotId;
    private String governedContext;
    private String status;
    private Instant startedAt;
    private Instant completedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID v) { this.taskId = v; }
    public UUID getContextSnapshotId() { return contextSnapshotId; }
    public void setContextSnapshotId(UUID v) { this.contextSnapshotId = v; }
    public String getGovernedContext() { return governedContext; }
    public void setGovernedContext(String v) { this.governedContext = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}
