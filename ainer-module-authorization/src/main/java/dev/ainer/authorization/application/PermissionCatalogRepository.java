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
     * 把权限定义 upsert 到目录投影（{@code ON CONFLICT (code) DO UPDATE}，
     * {@code definition_version} 递增）。完全相同的重复注册是幂等的；同批次内的同 code
     * 定义冲突由内存态 {@link dev.ainer.authorization.catalog.PermissionRegistry} 在启动时
     * fail-closed 拦截。已知边界：跨启动的定义漂移（已注册权限的元数据变更）会被投影
     * 静默覆盖，不产生告警。
     */
    void upsert(Permission permission, String sourceModule);

    Collection<Permission> findAll();
}
