package dev.ainer.authorization.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code ainer_authorization_change_audit} 的 MyBatis mapper（ADR-0030 §11.7）。
 * append-only——insert 是唯一的写操作。
 */
@Mapper
public interface AuthorizationChangeAuditMapper {

    /**
     * 插入一条变更审计行。{@code id} 列使用数据库 {@code DEFAULT uuidv7()}，
     * 不由调用方提供。返回受影响行数（必须为 1）。
     */
    int insert(@Param("row") AuthorizationChangeAuditRow row);
}
