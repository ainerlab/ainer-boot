package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限目录投影的 MyBatis mapper（ADR-0030 S1）。
 */
@Mapper
public interface PermissionMapper {

    /**
     * upsert 一条权限定义。使用 ON CONFLICT(code) 处理幂等重复注册。定义冲突由应用层
     * 在启动时通过 PermissionRegistry 检测。
     */
    int upsert(@Param("row") PermissionRow row, @Param("now") java.time.Instant now);

    List<PermissionRow> selectAll();
}
