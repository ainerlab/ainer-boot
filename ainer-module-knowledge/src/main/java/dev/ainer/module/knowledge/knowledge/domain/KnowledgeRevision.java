package dev.ainer.module.knowledge.knowledge.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 不可变语义负载（ADR-0044 K1）：状态由 append-only lifecycle 事件投影，行本身无更新路径。 */
public record KnowledgeRevision(
        UUID id, UUID objectId, long revisionNumber, String payloadMarkdown, String status,
        String createdByIssuer, String createdByType, String createdById, Instant createdAt,
        List<KnowledgeSource> sources, List<KnowledgeEvidence> evidence) {
}
