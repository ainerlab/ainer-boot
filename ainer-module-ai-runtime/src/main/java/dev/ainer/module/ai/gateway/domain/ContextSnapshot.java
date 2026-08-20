package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务执行时的上下文快照：固定证据引用与记忆引用的 JSON、快照时点与 schema 版本，
 * 保证结果可追溯到生成它时的上下文。
 */
public record ContextSnapshot(
        UUID id,
        UUID identityId,
        UUID identityVersionId,
        String evidenceRefsJson,
        String memoryRefsJson,
        Instant asOf,
        int schemaVersion,
        Instant createdAt) {

    public ContextSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(evidenceRefsJson, "evidenceRefsJson");
        Objects.requireNonNull(memoryRefsJson, "memoryRefsJson");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
