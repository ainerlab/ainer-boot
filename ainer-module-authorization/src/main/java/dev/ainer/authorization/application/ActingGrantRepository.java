package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.ActingGrant;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence port for {@link ActingGrant} aggregates (ADR-0043 A1). */
public interface ActingGrantRepository {

    /** Persisted grant carrying database identity alongside the domain value. */
    record PersistedGrant(
            UUID id,
            SubjectRef principal,
            UUID agentId,
            String agentVersion,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version,
            Set<PermissionCode> permissions) {
    }

    UUID save(SubjectRef principal, UUID agentId, String agentVersion, Set<PermissionCode> permissions,
            Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedGrant> findById(UUID id);

    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /** Live grants whose principal matches and whose validity window contains {@code at}. */
    List<PersistedGrant> findLiveGrants(SubjectRef principal, Instant at);
}
