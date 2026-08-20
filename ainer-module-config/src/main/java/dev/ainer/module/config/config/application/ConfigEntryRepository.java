package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ConfigEntry} 的持久化端口（ADR-0038）。
 */
public interface ConfigEntryRepository {

    UUID save(ConfigEntry entry);

    Optional<ConfigEntry> findByNamespaceAndKey(String namespace, String key);

    List<ConfigEntry> findByNamespace(String namespace);

    /** 更新已存在条目的值/版本。键不存在时返回 false。 */
    boolean update(UUID id, String value, String encryptedValue, long expectedVersion, long newVersion);
}
