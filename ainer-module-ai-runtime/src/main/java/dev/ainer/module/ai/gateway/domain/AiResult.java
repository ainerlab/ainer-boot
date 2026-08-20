package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次任务运行产出的结果：正文、事实引用（factRefs）与推理（inferences）的 JSON
 * 及结果 schema 版本。
 */
public record AiResult(
        UUID id,
        UUID runId,
        UUID invocationId,
        String content,
        String factRefsJson,
        String inferencesJson,
        int resultSchemaVersion,
        Instant createdAt) {

    public AiResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(factRefsJson, "factRefsJson");
        Objects.requireNonNull(inferencesJson, "inferencesJson");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
