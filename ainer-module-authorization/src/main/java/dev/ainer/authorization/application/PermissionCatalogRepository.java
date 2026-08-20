package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Permission;

import java.util.Collection;

/**
 * {@link Permission} 目录投影的持久化端口（ADR-0030 S1）。决策时的权威是内存态
 * {@link dev.ainer.authorization.catalog.PermissionRegistry}；本仓储把已注册定义同步到
 * 数据库管理投影。
 */
public interface PermissionCatalogRepository {

    /**
     * 把权限定义 upsert 到目录投影。若 code 已存在但定义不同，冲突会上抛给启动
     * fail-closed 处理；完全相同的重复注册是幂等的。
     */
    void upsert(Permission permission, String sourceModule);

    Collection<Permission> findAll();
}
