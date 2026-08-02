package dev.ainer.authorization.catalog;

import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory Permission catalog built from {@link PermissionContributor} registrations (ADR-0030 §3.2).
 * A duplicate code with an identical definition is idempotent; a duplicate code with a differing
 * definition is a startup-failing conflict. The registry is the authority for Permission metadata at
 * decision time; the database catalog is only a management projection of these registered definitions.
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
