package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DictionaryTypeMapper {
    UUID insertReturningId(@Param("row") DictionaryTypeRow row, @Param("now") java.time.Instant now);
    DictionaryTypeRow selectById(@Param("id") UUID id);
    DictionaryTypeRow selectActiveByCode(@Param("parentId") UUID parentId, @Param("code") String code);
    List<DictionaryTypeRow> selectByParentId(@Param("parentId") UUID parentId);
    List<DictionaryTypeRow> selectAllActive();
    List<DictionaryTypeRow> selectAll();
}
