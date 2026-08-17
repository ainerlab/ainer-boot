package dev.ainer.module.knowledge.knowledge.domain;

import java.time.Instant;
import java.util.UUID;

/** append-only 生命周期事件（ADR-0044 K2）：OBJECT_CREATED/PROPOSED/PUBLISHED/RETIRED。 */
public record KnowledgeLifecycleEvent(
        UUID id, UUID objectId, UUID revisionId, String event,
        String actorIssuer, String actorType, String actorId, Instant occurredAt) {
}
