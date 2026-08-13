package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Role;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for Role aggregates (ADR-0030 S1). Implemented by the infrastructure layer;
 * consumed by {@link RoleApplicationService}.
 */
public interface RoleRepository {

    /**
     * Persisted Role carrying its database identity alongside the domain {@link Role} value.
     *
     * @param id          database-generated UUIDv7 primary key
     * @param role        domain value (code + name + permissions)
     * @param systemRole  whether this is a built-in system role that cannot be deleted
     * @param version     optimistic-concurrency version
     * @param createdAt   row creation timestamp (set once on insert)
     * @param updatedAt   row last-modification timestamp (refreshed on version bump)
     */
    record RoleRecord(UUID id, Role role, boolean systemRole, long version,
                      java.time.Instant createdAt, java.time.Instant updatedAt) {
    }

    UUID save(Role role);

    Optional<RoleRecord> findById(UUID id);

    Optional<RoleRecord> findActiveByCode(String code);

    /**
     * Atomically replace the permission set of the role identified by {@code roleId}, provided the
     * current version matches {@code expectedVersion}. Returns the updated version, or empty if the
     * version check fails.
     */
    Optional<Long> replacePermissions(UUID roleId, Set<PermissionCode> permissions, long expectedVersion);

    Collection<RoleRecord> findAll();

    /**
     * Load the permissions granted by the given role IDs. Used by the binding resolver to reconstruct
     * domain {@link Role} instances without a separate round-trip per binding.
     */
    Set<PermissionCode> findPermissionCodesByRoleId(UUID roleId);
}
