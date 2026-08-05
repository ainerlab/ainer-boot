package dev.ainer.authorization.domain;

import java.util.Objects;
import java.util.Set;

/**
 * A named bundle of permissions (ADR-0030 §4.1). {@code OWNER/ADMIN/MEMBER} are built-in Identity/Workspace
 * roles and are not migrated or duplicated by this model.
 */
public record Role(String code, Set<PermissionCode> permissions) {

    public Role {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(permissions, "permissions");
        String normalized = code.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("role code must not be blank");
        }
        code = normalized;
        permissions = Set.copyOf(permissions);
    }

    public boolean grants(PermissionCode permission) {
        return permissions.contains(permission);
    }
}
