package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Assigns a {@link Role} and precise {@link Scope} to a subject over a validity window (ADR-0030 §4.1).
 * Bindings are revocable; a still-valid JWT cannot restore a revoked database grant.
 */
public record SubjectBinding(
        SubjectRef subject,
        Role role,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version) {

    public SubjectBinding {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        // validUntil may be null (open-ended)
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && role.grants(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
