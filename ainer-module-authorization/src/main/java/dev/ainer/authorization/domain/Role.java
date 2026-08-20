package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.Set;

/**
 * 命名的权限集合（ADR-0030 §4.1）。{@code OWNER/ADMIN/MEMBER} 是 Identity/Workspace 的
 * 内建角色，不迁移也不在本模型中复制。
 */
public record Role(String code, String name, Set<PermissionCode> permissions) {

    public Role {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(permissions, "permissions");
        String normalizedCode = code.trim();
        String normalizedName = name.trim();
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("role code must not be blank");
        }
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("role name must not be blank");
        }
        code = normalizedCode;
        name = normalizedName;
        permissions = Set.copyOf(permissions);
    }

    public boolean grants(PermissionCode permission) {
        return permissions.contains(permission);
    }
}
