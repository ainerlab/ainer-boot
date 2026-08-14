package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for {@code ainer_dictionary_audit}; SQL lives in {@code mapper/dictionary/DictionaryAuditMapper.xml}. */
@Mapper
public interface DictionaryAuditMapper {

    int insert(@Param("row") DictionaryAuditRow row);
}
