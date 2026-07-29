package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
