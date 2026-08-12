package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DictionaryItemMapper {
    UUID insertReturningId(@Param("row") DictionaryItemRow row, @Param("now") java.time.Instant now);
    DictionaryItemRow selectById(@Param("id") UUID id);
    DictionaryItemRow selectActiveByCode(@Param("typeId") UUID typeId, @Param("code") String code);
    List<DictionaryItemRow> selectActiveByTypeId(@Param("typeId") UUID typeId);
}
