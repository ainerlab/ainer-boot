package dev.ainer.module.config.config.api;

import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API models for the config management surface (ADR-0040). */
public final class ConfigApiDtos {

    private ConfigApiDtos() {
    }

    public record SetValueRequest(
            String namespace,
            String key,
            String value,
            String valueType,
            @Nullable String description) {
    }

    public record SetSecretRequest(
            String namespace,
            String key,
            String plaintext,
            String valueType,
            @Nullable String description) {
    }

    /**
     * Entry projection. Secret entries never expose value or ciphertext — only the fact that a
     * secret exists at this key with its version.
     */
    public record ConfigEntryResponse(
            UUID id,
            String namespace,
            String key,
            @Nullable String value,
            String valueType,
            boolean secret,
            @Nullable String description,
            long version) {

        public static ConfigEntryResponse from(ConfigEntry entry) {
            return new ConfigEntryResponse(
                    entry.id(),
                    entry.namespace(),
                    entry.key(),
                    entry.secret() ? null : entry.value(),
                    entry.valueType().name(),
                    entry.secret(),
                    entry.description(),
                    entry.version());
        }
    }

    public record ConfigEntryListResponse(List<ConfigEntryResponse> items) {
    }

    public record ConfigHistoryResponse(
            UUID id,
            UUID entryId,
            String namespace,
            String key,
            @Nullable String oldValue,
            @Nullable String newValue,
            @Nullable Long oldVersion,
            @Nullable Long newVersion,
            @Nullable String changedByType,
            @Nullable String changedById,
            Instant changedAt) {

        public static ConfigHistoryResponse from(ConfigHistory history) {
            return new ConfigHistoryResponse(
                    history.id(),
                    history.entryId(),
                    history.namespace(),
                    history.key(),
                    history.oldValue(),
                    history.newValue(),
                    history.oldVersion(),
                    history.newVersion(),
                    history.changedByType(),
                    history.changedById(),
                    history.changedAt());
        }
    }

    public record ConfigHistoryListResponse(List<ConfigHistoryResponse> items) {
    }
}
