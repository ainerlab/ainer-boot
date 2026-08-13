package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlled binding of one rotatable OAuth {@code client_id} to a stable {@link ServicePrincipal}
 * (ADR-0033 Greenfield §2.6).
 *
 * <p>A ServicePrincipal may hold 1..n OAuthClientBindings, but at most one {@code ACTIVE} binding per
 * {@code client_id} at a time (partial unique index on {@code (client_id) WHERE status = 'ACTIVE'}).
 * Rotating a credential retires the prior binding and creates a fresh ACTIVE one; the retired record stays
 * for audit and so historical tokens remain introspectable. Credential material (client secret) is NOT
 * stored here — it lives in the OAuth registered-client store referenced by {@code client_id}.
 */
public record OAuthClientBinding(
        UUID bindingId,
        UUID principalId,
        String clientId,
        OAuthClientBindingStatus status,
        Instant boundAt,
        @Nullable Instant unboundAt) {

    public OAuthClientBinding {
        Objects.requireNonNull(bindingId, "bindingId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(boundAt, "boundAt");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must be non-blank");
        }
        if (status == OAuthClientBindingStatus.ACTIVE && unboundAt != null) {
            throw new IllegalArgumentException("an ACTIVE binding must not carry an unboundAt timestamp");
        }
    }

    /** Whether this binding currently admits the linked client_id. */
    public boolean isActive() {
        return status == OAuthClientBindingStatus.ACTIVE;
    }
}
