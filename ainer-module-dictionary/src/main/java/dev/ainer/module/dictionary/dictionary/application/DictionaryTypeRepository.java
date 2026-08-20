package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DictionaryType} 聚合的持久化端口（ADR-0038/0040）。
 */
public interface DictionaryTypeRepository {

    UUID save(DictionaryType type);

    Optional<DictionaryType> findById(UUID id);

    Optional<DictionaryType> findActiveByCode(UUID parentId, String code);

    List<DictionaryType> findByParentId(UUID parentId);

    List<DictionaryType> findAllActive();

    Collection<DictionaryType> findAll();

    /**
     * 乐观锁部分更新；{@code null} 字段保留库中已有值。
     * 行不存在或版本过期时返回 false。
     */
    boolean update(UUID id, @Nullable String name, @Nullable String nameEn,
            @Nullable String description, @Nullable Integer sortIndex,
            long expectedVersion, long newVersion);

    /** 乐观锁状态迁移。行不存在或版本过期时返回 false。 */
    boolean updateStatus(UUID id, DictionaryStatus status, long expectedVersion, long newVersion);

    /** 分页遍历类型，可按状态过滤，按 sortIndex/code 排序。 */
    DictionaryPageSlice<DictionaryType> findPage(@Nullable String status, long offset, int size);
}
