package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code ainer_dictionary_type} 的 MyBatis mapper；SQL 位于 {@code mapper/dictionary/DictionaryTypeMapper.xml}。
 * 更新与状态迁移语句携带 {@code expectedVersion}/{@code newVersion} 参数，由 XML 实现乐观锁条件更新。
 */
@Mapper
public interface DictionaryTypeMapper {
    UUID insertReturningId(@Param("row") DictionaryTypeRow row, @Param("now") Instant now);
    DictionaryTypeRow selectById(@Param("id") UUID id);
    DictionaryTypeRow selectActiveByCode(@Param("parentId") UUID parentId, @Param("code") String code);
    List<DictionaryTypeRow> selectByParentId(@Param("parentId") UUID parentId);
    List<DictionaryTypeRow> selectAllActive();
    List<DictionaryTypeRow> selectAll();
    int update(@Param("id") UUID id, @Param("name") @Nullable String name,
            @Param("nameEn") @Nullable String nameEn, @Param("description") @Nullable String description,
            @Param("sortIndex") @Nullable Integer sortIndex,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    int updateStatus(@Param("id") UUID id, @Param("status") String status,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    List<DictionaryTypeRow> selectPage(@Nullable @Param("status") String status,
            @Param("offset") long offset, @Param("limit") int limit);
    long countPage(@Nullable @Param("status") String status);
}
