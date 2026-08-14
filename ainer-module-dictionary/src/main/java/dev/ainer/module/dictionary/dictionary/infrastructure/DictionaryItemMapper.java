package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DictionaryItemMapper {
    UUID insertReturningId(@Param("row") DictionaryItemRow row, @Param("now") Instant now);
    DictionaryItemRow selectById(@Param("id") UUID id);
    DictionaryItemRow selectActiveByCode(@Param("typeId") UUID typeId, @Param("code") String code);
    List<DictionaryItemRow> selectActiveByTypeId(@Param("typeId") UUID typeId);
    int update(@Param("id") UUID id, @Param("label") @Nullable String label,
            @Param("labelEn") @Nullable String labelEn, @Param("value") @Nullable String value,
            @Param("sortIndex") @Nullable Integer sortIndex, @Param("cssClass") @Nullable String cssClass,
            @Param("remark") @Nullable String remark,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    int updateStatus(@Param("id") UUID id, @Param("status") String status,
            @Param("expectedVersion") long expectedVersion, @Param("newVersion") long newVersion,
            @Param("now") Instant now);
    List<DictionaryItemRow> selectPage(@Param("typeId") UUID typeId,
            @Param("offset") long offset, @Param("limit") int limit);
    long countPage(@Param("typeId") UUID typeId);
}
