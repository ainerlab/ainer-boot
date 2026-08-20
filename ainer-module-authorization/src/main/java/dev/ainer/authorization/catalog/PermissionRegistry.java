package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 由 {@link PermissionContributor} 注册构建的内存态权限目录（ADR-0030 §3.2）。同 code 且
 * 定义完全相同的重复注册是幂等的；同 code 但定义不同的注册是启动即失败的冲突。决策时
 * 该注册表是权限元数据的权威；数据库目录只是这些已注册定义的管理投影。
 */
public final class PermissionRegistry {

    private final Map<PermissionCode, Permission> permissions = new HashMap<>();

    public PermissionRegistry register(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        Permission existing = permissions.putIfAbsent(permission.code(), permission);
        if (existing != null && !existing.equals(permission)) {
            throw new IllegalStateException(
                    "Duplicate permission code '%s': conflicting definitions".formatted(permission.code().value()));
        }
        return this;
    }

    public PermissionRegistry register(PermissionContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        contributor.contribute().forEach(this::register);
        return this;
    }

    public Optional<Permission> find(PermissionCode code) {
        Objects.requireNonNull(code, "code");
        return Optional.ofNullable(permissions.get(code));
    }

    public Set<Permission> snapshot() {
        return Set.copyOf(permissions.values());
    }
}
