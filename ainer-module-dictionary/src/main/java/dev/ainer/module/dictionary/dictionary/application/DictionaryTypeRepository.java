package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link DictionaryType} aggregates (ADR-0038/0040).
 */
public interface DictionaryTypeRepository {

    UUID save(DictionaryType type);

    Optional<DictionaryType> findById(UUID id);

    Optional<DictionaryType> findActiveByCode(UUID parentId, String code);

    List<DictionaryType> findByParentId(UUID parentId);

    List<DictionaryType> findAllActive();

    Collection<DictionaryType> findAll();

    /**
     * Partial update with optimistic locking; {@code null} fields keep their stored value.
     * Returns false when the row does not exist or the version is stale.
     */
    boolean update(UUID id, @Nullable String name, @Nullable String nameEn,
            @Nullable String description, @Nullable Integer sortIndex,
            long expectedVersion, long newVersion);

    /** Status transition with optimistic locking. Returns false on missing row or stale version. */
    boolean updateStatus(UUID id, DictionaryStatus status, long expectedVersion, long newVersion);

    /** Page through types, optionally filtered by status, ordered by sortIndex/code. */
    DictionaryPageSlice<DictionaryType> findPage(@Nullable String status, long offset, int size);
}
