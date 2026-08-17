package dev.ainer.module.knowledge.knowledge.domain;

import java.util.UUID;

/** 来源引用（ADR-0044 K2）：非身份，不自动等于真实。 */
public record KnowledgeSource(UUID id, UUID revisionId, String sourceType, String sourceRef) {
}
