package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.Permission;

import java.util.Set;

/**
 * 由贡献模块提供其已实现 {@link Permission} 定义的有限集合（ADR-0030 §3.2）。权限目录是
 * 管理投影；管理员无法创建应用代码未实现的任意权限字符串。
 */
@FunctionalInterface
public interface PermissionContributor {

    Set<Permission> contribute();
}
