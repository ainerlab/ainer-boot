package dev.ainer.module.file.file.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for {@code ainer_file_audit}; SQL lives in {@code mapper/file/FileAuditMapper.xml}. */
@Mapper
public interface FileAuditMapper {

    int insert(@Param("row") FileAuditRow row);
}
