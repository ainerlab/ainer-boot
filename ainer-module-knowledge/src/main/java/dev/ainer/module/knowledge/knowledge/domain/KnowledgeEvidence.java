package dev.ainer.module.knowledge.knowledge.domain;

import java.util.UUID;

/** 类型化证据链接（ADR-0044 K2）：SUPPORTS/CONTRADICTS，append-only。 */
public record KnowledgeEvidence(UUID id, UUID revisionId, String linkType, String targetRef) {
}
