package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigHistory;

import java.util.List;
import java.util.UUID;

/**
 * Persistence port for {@link ConfigHistory} (ADR-0038). Append-only.
 */
public interface ConfigHistoryRepository {

    void insert(ConfigHistory history);

    List<ConfigHistory> findByEntryId(UUID entryId);
}
