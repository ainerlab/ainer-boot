package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.PermissionCode;

/**
 * 把 OAuth scope 映射到它允许的权限（ADR-0030 §3.3）。scope 绝不因名称相同而隐式变成
 * 权限；只有这个显式的 ceiling 映射器才能为已认证路径授权。
 */
@FunctionalInterface
public interface ScopePermissionCeiling {

    boolean permits(String scope, PermissionCode permission);
}
