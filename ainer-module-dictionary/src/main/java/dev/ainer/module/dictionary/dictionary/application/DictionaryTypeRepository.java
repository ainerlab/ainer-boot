package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link DictionaryType} aggregates (ADR-0038).
 */
public interface DictionaryTypeRepository {

    UUID save(DictionaryType type);

    Optional<DictionaryType> findById(UUID id);

    Optional<DictionaryType> findActiveByCode(UUID parentId, String code);

    List<DictionaryType> findByParentId(UUID parentId);

    List<DictionaryType> findAllActive();

    Collection<DictionaryType> findAll();
}
