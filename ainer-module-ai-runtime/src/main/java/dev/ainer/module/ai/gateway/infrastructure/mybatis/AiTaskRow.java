package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class AiTaskRow {
    private UUID id;
    private UUID workspaceId;
    private String taskType;
    private UUID targetIdentityId;
    private String status;
    private String trigger;
    private String triggeredBy;
    private String policyVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID v) { this.workspaceId = v; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String v) { this.taskType = v; }
    public UUID getTargetIdentityId() { return targetIdentityId; }
    public void setTargetIdentityId(UUID v) { this.targetIdentityId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String v) { this.trigger = v; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String v) { this.triggeredBy = v; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String v) { this.policyVersion = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
