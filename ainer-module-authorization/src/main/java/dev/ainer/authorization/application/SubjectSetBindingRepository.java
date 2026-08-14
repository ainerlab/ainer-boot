package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectSetBinding;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for subject-set bindings (ADR-0042 O2), mirroring the direct-binding port. */
public interface SubjectSetBindingRepository {

    /** Persisted set binding with database identity and role reference. */
    record PersistedSetBinding(
            UUID id,
            SubjectSetRef set,
            UUID roleId,
            Scope scope,
            BindingStatus status,
            Instant validFrom,
            Instant validUntil,
            long version) {
    }

    UUID save(SubjectSetRef set, UUID roleId, Scope scope, Instant validFrom, Instant validUntil);

    Optional<PersistedSetBinding> findById(UUID id);

    Optional<Long> revoke(UUID id, Instant revokedAt, String reason);

    /** Live (ACTIVE, time-covered) set bindings whose scope covers the resource; SQL-side filtered. */
    List<PersistedSetBinding> findLiveSetBindings(ResourceRef resource, Instant at);
}
