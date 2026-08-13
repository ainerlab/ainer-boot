package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link DictionaryItem} entries (ADR-0038).
 */
public interface DictionaryItemRepository {

    UUID save(DictionaryItem item);

    Optional<DictionaryItem> findById(UUID id);

    Optional<DictionaryItem> findActiveByCode(UUID typeId, String code);

    List<DictionaryItem> findActiveByTypeId(UUID typeId);
}
