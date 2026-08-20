package dev.ainer.module.config.config.api;

import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 配置管理面的 API 模型（ADR-0040）。 */
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
     * 条目投影。secret 条目绝不暴露明文值或密文——
     * 只给出该键下存在 secret 及其版本这一事实。
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
