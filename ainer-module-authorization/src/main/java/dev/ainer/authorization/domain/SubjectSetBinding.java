package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigns a {@link Role} and precise {@link Scope} to a {@link SubjectSetRef} over a validity
 * window (ADR-0042 O2）。Shares Role/Scope/time/revocation semantics with direct
 * {@link SubjectBinding}s; the requesting subject gains the grant only through decision-time set
 * membership. GLOBAL scopes and system-only/HIGH-risk permissions are rejected at creation — the
 * engine additionally never serves GLOBAL from a set binding.
 */
public record SubjectSetBinding(
        UUID id,
        SubjectSetRef set,
        Role role,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version) {

    public SubjectSetBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        if (scope instanceof Scope.Global) {
            throw new IllegalArgumentException("SubjectSetBinding must not use a GLOBAL scope");
        }
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && role.grants(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
