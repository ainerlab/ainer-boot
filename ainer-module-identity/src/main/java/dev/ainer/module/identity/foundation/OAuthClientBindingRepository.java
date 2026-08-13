package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link OAuthClientBinding} (ADR-0033 Greenfield §2.6).
 *
 * <p>The binding links a rotatable OAuth {@code client_id} to a stable {@link ServicePrincipal}. The
 * partial unique index on {@code (client_id) WHERE status = 'ACTIVE'} guarantees at most one active binding
 * per credential at a time; retired bindings are retained for audit and historical introspection.
 */
public interface OAuthClientBindingRepository {

    void save(OAuthClientBinding binding);

    Optional<OAuthClientBinding> findActiveByClientId(String clientId);

    Optional<OAuthClientBinding> findByPrincipalId(UUID principalId);

    /** Next PostgreSQL UUIDv7 primary key for a binding row. */
    UUID nextUuidV7();
}
