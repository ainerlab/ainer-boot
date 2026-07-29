package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AiTask(
        UUID id,
        UUID tenantId,
        UUID workspaceId,
        String taskType,
        UUID targetIdentityId,
        AiTaskStatus status,
        String trigger,
        String triggeredBy,
        String policyVersion,
        Instant createdAt,
        Instant updatedAt) {

    public AiTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(triggeredBy, "triggeredBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
