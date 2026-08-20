package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DictionaryItem} 条目的持久化端口（ADR-0038/0040）。
 */
public interface DictionaryItemRepository {

    UUID save(DictionaryItem item);

    Optional<DictionaryItem> findById(UUID id);

    Optional<DictionaryItem> findActiveByCode(UUID typeId, String code);

    List<DictionaryItem> findActiveByTypeId(UUID typeId);

    /**
     * 乐观锁部分更新；{@code null} 字段保留库中已有值。
     * 行不存在或版本过期时返回 false。
     */
    boolean update(UUID id, @Nullable String label, @Nullable String labelEn,
            @Nullable String value, @Nullable Integer sortIndex,
            @Nullable String cssClass, @Nullable String remark,
            long expectedVersion, long newVersion);

    /** 乐观锁状态迁移。行不存在或版本过期时返回 false。 */
    boolean updateStatus(UUID id, DictionaryStatus status, long expectedVersion, long newVersion);

    /** 分页遍历某一类型的字典项（含全部状态），按 sortIndex/code 排序。 */
    DictionaryPageSlice<DictionaryItem> findPage(UUID typeId, long offset, int size);
}
