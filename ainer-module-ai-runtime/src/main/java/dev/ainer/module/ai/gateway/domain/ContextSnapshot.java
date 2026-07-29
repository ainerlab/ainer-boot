package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ContextSnapshot(
        UUID id,
        UUID tenantId,
        UUID identityId,
        UUID identityVersionId,
        String evidenceRefsJson,
        String memoryRefsJson,
        Instant asOf,
        int schemaVersion,
        Instant createdAt) {

    public ContextSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(evidenceRefsJson, "evidenceRefsJson");
        Objects.requireNonNull(memoryRefsJson, "memoryRefsJson");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
