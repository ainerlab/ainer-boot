package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigHistory;

import java.util.List;
import java.util.UUID;

/**
 * {@link ConfigHistory} 的持久化端口（ADR-0038）。只追加。
 */
public interface ConfigHistoryRepository {

    void insert(ConfigHistory history);

    List<ConfigHistory> findByEntryId(UUID entryId);
}
