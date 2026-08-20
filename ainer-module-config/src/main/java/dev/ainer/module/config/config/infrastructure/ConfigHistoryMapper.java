package dev.ainer.module.config.config.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * {@code ainer_config_history} 的 MyBatis mapper；SQL 位于 {@code mapper/config/ConfigHistoryMapper.xml}。
 * 变更历史只追加写入，按 entryId 查询。
 */
@Mapper
public interface ConfigHistoryMapper {
    int insert(@Param("row") ConfigHistoryRow row);
    List<ConfigHistoryRow> selectByEntryId(@Param("entryId") UUID entryId);
}
