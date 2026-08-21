package dev.ainer.module.task.tasks.domain;

import java.time.Instant;
import java.util.UUID;

/** 任务类型定义（ADR-0047）：产品注册的任务类型与执行参数。 */
public record TaskDefinition(
        UUID id, String taskType, String displayName, String handlerRef,
        int maxAttempts, int timeoutSeconds, String status,
        Instant createdAt, Instant updatedAt) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
