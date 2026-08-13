package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for {@link SubjectBinding} aggregates (ADR-0030 S1). Implemented by the
 * infrastructure layer; consumed by {@link SubjectBindingApplicationService} and the PostgreSQL
 * {@code BindingResolver}.
 */
public interface SubjectBindingRepository {

    /**
     * Persisted binding carrying its database identity and role reference alongside the domain value.
     *
     * @param id          database-generated UUIDv7 primary key
     * @param subjectRef  who the binding is for
     * @param roleId      the persisted role this binding references
     * @param scope       structured scope (Global / Workspace / Resource)
     * @param status      ACTIVE or REVOKED
     * @param validFrom   validity window start (inclusive)
     * @param validUntil  validity window end (exclusive), or null for open-ended
     * @param version     optimistic-concurrency version
     * @param revokedAt   when the binding was revoked, or null if active
     * @param revokedReason free-text revocation reason, or null
     */
    record PersistedBinding(
            UUID id,
            SubjectRef subjectRef,
            UUID roleId,
            String roleCode,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version,
            Instant revokedAt,
            String revokedReason) {
    }

    UUID save(SubjectRef subject, UUID roleId, Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedBinding> findById(UUID id);

    /**
     * Revoke (logically) the binding identified by {@code id}. Sets status to REVOKED, records the
     * revocation timestamp and reason, and increments the version. Returns empty if the binding does
     * not exist or is already revoked.
     *
     * @param id        binding primary key
     * @param revokedAt revocation timestamp
     * @param reason    free-text reason stored in the audit trail
     * @return the updated version if the revocation succeeded, or empty if not found / already revoked
     */
    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /**
     * Return all ACTIVE bindings for the given subject whose validity window contains {@code at}.
     * This is the query used by the PostgreSQL {@code BindingResolver}; revoked or expired bindings
     * are excluded at the database level — there is no ALLOW cache.
     */
    List<PersistedBinding> findLiveBindings(SubjectRef subject, Instant at);

    /**
     * Return all bindings (including revoked/expired) for the given subject, ordered by creation.
     * Used by management queries, not by the decision engine.
     */
    List<PersistedBinding> findAllBySubject(SubjectRef subject);
}
