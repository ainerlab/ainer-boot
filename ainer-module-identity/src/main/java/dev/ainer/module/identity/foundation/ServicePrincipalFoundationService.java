package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.security.principal.IdentityAuthorityRef;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application core for the Greenfield ServicePrincipal model (ADR-0033 Greenfield §2.6, S1.1 spine).
 *
 * <p>Exercises {@link ServicePrincipal} + {@link OAuthClientBinding} via the foundation ports. The principal
 * is the audit-stable non-human identity; an OAuth {@code client_id} is a rotatable credential bound to it.
 * Client rotation must never change the audit identity, and a binding collision (an ACTIVE binding already
 * exists for the same client_id) is a hard conflict.
 *
 * <p>Like {@link IdentityFoundationService}, this service is deliberately decoupled from the legacy
 * tenant-bound services and does not touch them. It is the working core that the destructive cutover wires
 * into the token-issuance path to project stable {@code ServiceSubjectRef}s from rotatable credentials.
 *
 * <p>Not annotated {@code @Service}: the {@code Supplier<UUID>} id source is bound to the foundation
 * repository's {@code nextUuidV7()} in {@code IdentityModuleConfiguration}, so the bean is declared explicitly
 * there rather than auto-wired with an ambiguous {@code Supplier}.
 */
public class ServicePrincipalFoundationService {

    private final ServicePrincipalRepository principalRepository;
    private final OAuthClientBindingRepository bindingRepository;
    private final Clock clock;
    private final Supplier<UUID> idSource;

    public ServicePrincipalFoundationService(
            ServicePrincipalRepository principalRepository,
            OAuthClientBindingRepository bindingRepository,
            Clock clock,
            Supplier<UUID> idSource) {
        this.principalRepository = Objects.requireNonNull(principalRepository, "principalRepository");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
    }

    /**
     * Register a new ACTIVE ServicePrincipal. Fails closed if a principal already exists for the same id.
     */
    public ServicePrincipal registerServicePrincipal(IdentityAuthorityRef authority) {
        Objects.requireNonNull(authority, "authority");
        Instant now = clock.instant();
        ServicePrincipal principal = new ServicePrincipal(
                idSource.get(), authority, ServicePrincipalStatus.ACTIVE, 0L, now);
        principalRepository.save(principal);
        return principal;
    }

    /**
     * Bind a rotatable OAuth client_id to an existing ACTIVE ServicePrincipal. Fails closed if the principal
     * does not exist, is not active, or already carries an ACTIVE binding for the same client_id.
     */
    public OAuthClientBinding bindClient(UUID principalId, String clientId) {
        Objects.requireNonNull(principalId, "principalId");
        requireNonBlank(clientId, "clientId");
        ServicePrincipal principal = principalRepository.findByPrincipalId(principalId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.SERVICE_PRINCIPAL_NOT_FOUND));
        if (!principal.status().canAuthenticate()) {
            throw new BusinessException(IdentityErrorCode.SERVICE_PRINCIPAL_NOT_ACTIVE);
        }
        if (bindingRepository.findActiveByClientId(clientId).isPresent()) {
            throw new BusinessException(IdentityErrorCode.OAUTH_CLIENT_BINDING_ALREADY_EXISTS);
        }
        OAuthClientBinding binding = new OAuthClientBinding(
                idSource.get(), principalId, clientId, OAuthClientBindingStatus.ACTIVE,
                clock.instant(), null);
        bindingRepository.save(binding);
        return binding;
    }

    /** Resolve the stable principal backing a rotatable OAuth client_id, if any. */
    public Optional<ServicePrincipal> findPrincipalByClientId(String clientId) {
        requireNonBlank(clientId, "clientId");
        return principalRepository.findByActiveClientId(clientId);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }
}
