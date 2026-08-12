package dev.ainer.module.config.config.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ConfigHistoryMapper {
    int insert(@Param("row") ConfigHistoryRow row);
    List<ConfigHistoryRow> selectByEntryId(@Param("entryId") UUID entryId);
}
