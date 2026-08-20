package dev.ainer.module.file.file.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code ainer_file_audit} 的 MyBatis mapper；SQL 位于 {@code mapper/file/FileAuditMapper.xml}。 */
@Mapper
public interface FileAuditMapper {

    int insert(@Param("row") FileAuditRow row);
}
