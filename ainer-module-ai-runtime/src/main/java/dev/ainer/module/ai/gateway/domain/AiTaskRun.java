package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AiTaskRun(
        UUID id,
        UUID taskId,
        UUID contextSnapshotId,
        String governedContextJson,
        AiTaskRunStatus status,
        Instant startedAt,
        Instant completedAt) {

    public AiTaskRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(contextSnapshotId, "contextSnapshotId");
        Objects.requireNonNull(governedContextJson, "governedContextJson");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
