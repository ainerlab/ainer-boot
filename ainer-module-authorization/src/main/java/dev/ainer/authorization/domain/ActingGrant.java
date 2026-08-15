package dev.ainer.authorization.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One-layer principal→agent delegation (ADR-0043 A1). The grant carries an explicit minimal
 * permission subset and a single structured scope; it never widens the principal's authority —
 * each checkpoint re-resolves the principal's live bindings and the agent status (pull-based, no
 * cache). Grants are non-delegable by construction; GLOBAL is not expressible.
 */
public record ActingGrant(
        UUID id,
        SubjectRef principal,
        UUID agentId,
        String agentVersion,
        Scope scope,
        BindingStatus status,
        Instant validFrom,
        @Nullable Instant validUntil,
        long version,
        Set<PermissionCode> permissions) {

    public ActingGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agentVersion, "agentVersion");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(permissions, "permissions");
        if (scope instanceof Scope.Global) {
            throw new IllegalArgumentException("ActingGrant must not use a GLOBAL scope");
        }
        permissions = Set.copyOf(permissions);
    }

    public boolean isLive(PermissionCode permission, ResourceRef resource, Instant at) {
        return status == BindingStatus.ACTIVE
                && permissions.contains(permission)
                && scope.covers(resource)
                && !at.isBefore(validFrom)
                && (validUntil == null || at.isBefore(validUntil));
    }
}
