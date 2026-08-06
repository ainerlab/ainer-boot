package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link ServicePrincipal} (ADR-0033 Greenfield §2.6).
 *
 * <p>Production implements this against the rebuilt Identity baseline (PostgreSQL UUIDv7 primary keys,
 * authority-qualified lookup). The port exposes only the typed aggregate; callers never consume raw storage.
 * Resolution by OAuth {@code client_id} crosses the {@link OAuthClientBindingRepository} boundary internally
 * so token issuance can project a stable {@code ServiceSubjectRef} from a rotatable credential.
 */
public interface ServicePrincipalRepository {

    void save(ServicePrincipal principal);

    Optional<ServicePrincipal> findByPrincipalId(UUID principalId);

    /**
     * Resolve the principal backing an OAuth client_id via its currently ACTIVE binding. Returns empty when
     * no active binding exists for the credential.
     */
    Optional<ServicePrincipal> findByActiveClientId(String clientId);

    /** Next PostgreSQL UUIDv7 primary key for the principal aggregate. */
    UUID nextUuidV7();
}
