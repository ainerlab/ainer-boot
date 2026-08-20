package dev.ainer.module.dictionary.dictionary.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code ainer_dictionary_audit} 的 MyBatis mapper；SQL 位于 {@code mapper/dictionary/DictionaryAuditMapper.xml}。 */
@Mapper
public interface DictionaryAuditMapper {

    int insert(@Param("row") DictionaryAuditRow row);
}
