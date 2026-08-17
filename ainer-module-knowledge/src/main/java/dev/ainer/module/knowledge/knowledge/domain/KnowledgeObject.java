package dev.ainer.module.knowledge.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

/** 稳定语义身份（ADR-0044 K1）：锚定 Workspace，正文在 Revision。 */
public record KnowledgeObject(
        UUID id, UUID workspaceId, String kind, String title, String status,
        Instant createdAt, Instant updatedAt) {
}
