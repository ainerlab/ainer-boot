package dev.ainer.module.identity.foundation;

import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable non-human security principal within one {@link IdentityAuthorityRef} (ADR-0033 Greenfield §2.6).
 *
 * <p>A ServicePrincipal is the audit-stable identity of a non-human caller. OAuth {@code client_id}s are
 * rotatable credentials bound to it (see {@link OAuthClientBinding}), not the principal itself: client
 * rotation must never change the audit identity. A service can never hold a human WorkspaceMembership or a
 * governance OWNER role.
 *
 * <p>{@code securityEpoch} mirrors {@link HumanAccount#securityEpoch()}: a monotonic principal-wide
 * revocation version — credentials, sessions and tokens minted before the current epoch are invalid. It is
 * the Greenfield replacement for the legacy service-client lookup that conflated {@code client_id} with
 * subject.
 */
public record ServicePrincipal(
        UUID principalId,
        IdentityAuthorityRef authority,
        ServicePrincipalStatus status,
        long securityEpoch,
        Instant createdAt) {

    public ServicePrincipal {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (securityEpoch < 0) {
            throw new IllegalArgumentException("securityEpoch must be non-negative");
        }
    }

    /**
     * The authority-qualified principal reference used by token issuance, authorization and audit. The
     * {@code servicePrincipalId} is the stable UUID string of this principal, never a rotatable client_id.
     */
    public ServiceSubjectRef toSubjectRef() {
        return new ServiceSubjectRef(authority, principalId.toString());
    }
}
