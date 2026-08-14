package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link DictionaryItem} entries (ADR-0038/0040).
 */
public interface DictionaryItemRepository {

    UUID save(DictionaryItem item);

    Optional<DictionaryItem> findById(UUID id);

    Optional<DictionaryItem> findActiveByCode(UUID typeId, String code);

    List<DictionaryItem> findActiveByTypeId(UUID typeId);

    /**
     * Partial update with optimistic locking; {@code null} fields keep their stored value.
     * Returns false when the row does not exist or the version is stale.
     */
    boolean update(UUID id, @Nullable String label, @Nullable String labelEn,
            @Nullable String value, @Nullable Integer sortIndex,
            @Nullable String cssClass, @Nullable String remark,
            long expectedVersion, long newVersion);

    /** Status transition with optimistic locking. Returns false on missing row or stale version. */
    boolean updateStatus(UUID id, DictionaryStatus status, long expectedVersion, long newVersion);

    /** Page through items of one type (all statuses), ordered by sortIndex/code. */
    DictionaryPageSlice<DictionaryItem> findPage(UUID typeId, long offset, int size);
}
