package dev.ainer.module.task.tasks.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** 任务实例（ADR-0047）：一次延迟/周期执行的具体作业。 */
public record TaskJob(
        UUID id, String taskType, String payloadJson, String status,
        int attemptCount, int maxAttempts,
        Instant nextRunAt, @Nullable Long intervalSeconds,
        @Nullable String lockedBy, @Nullable Instant lockedAt,
        @Nullable String lastError,
        String createdByIssuer, String createdByType, String createdById,
        Instant createdAt, Instant updatedAt, @Nullable Instant completedAt) {

    public boolean terminal() {
        return "SUCCEEDED".equals(status) || "EXHAUSTED".equals(status)
                || "CANCELLED".equals(status);
    }

    public boolean periodic() {
        return intervalSeconds != null && intervalSeconds > 0;
    }
}
