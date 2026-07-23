package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class WorkspaceAuthorizationAuditRow {

    private UUID id;
    private String tenantId;
    private UUID workspaceId;
    private String actorSubjectId;
    private String targetSubjectId;
    private String action;
    private String decision;
    private String reasonCode;
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getActorSubjectId() {
        return actorSubjectId;
    }

    public void setActorSubjectId(String actorSubjectId) {
        this.actorSubjectId = actorSubjectId;
    }

    public String getTargetSubjectId() {
        return targetSubjectId;
    }

    public void setTargetSubjectId(String targetSubjectId) {
        this.targetSubjectId = targetSubjectId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
