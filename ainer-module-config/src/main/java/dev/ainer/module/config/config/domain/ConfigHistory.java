package dev.ainer.module.config.config.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单次配置变更的只追加记录（ADR-0038 §3 变更审计）。
 */
public record ConfigHistory(
        UUID id,
        UUID entryId,
        String namespace,
        String key,
        @Nullable String oldValue,
        @Nullable String newValue,
        @Nullable Long oldVersion,
        @Nullable Long newVersion,
        @Nullable String changedByIssuer,
        @Nullable String changedByType,
        @Nullable String changedById,
        Instant changedAt) {

    public ConfigHistory {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(changedAt, "changedAt");
    }
}
