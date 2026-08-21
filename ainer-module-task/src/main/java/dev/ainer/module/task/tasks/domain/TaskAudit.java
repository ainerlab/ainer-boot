package dev.ainer.module.task.tasks.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** 任务事件审计（append-only）。 */
public record TaskAudit(
        UUID id, UUID jobId, String event, @Nullable Integer attempt,
        @Nullable String actorIssuer, @Nullable String actorType, @Nullable String actorId,
        @Nullable String detail, Instant occurredAt) {
}
