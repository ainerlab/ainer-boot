package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link ConfigEntry} (ADR-0038).
 */
public interface ConfigEntryRepository {

    UUID save(ConfigEntry entry);

    Optional<ConfigEntry> findByNamespaceAndKey(String namespace, String key);

    List<ConfigEntry> findByNamespace(String namespace);

    /** Update value/version for an existing entry. Returns false if the key does not exist. */
    boolean update(UUID id, String value, String encryptedValue, long expectedVersion, long newVersion);
}
