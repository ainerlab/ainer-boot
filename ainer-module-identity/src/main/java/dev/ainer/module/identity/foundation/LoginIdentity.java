package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Controlled binding of one authentication identifier to a {@link HumanAccount} (ADR-0033 Greenfield §4).
 *
 * <p>A HumanAccount holds 1..n LoginIdentities (the aggregate's credential-less invariant: no HumanAccount
 * without at least one verified binding, once it has authenticated). {@code providerAuthority} + {@code type}
 * + {@code normalizedIdentifier} is the uniqueness key for a binding; the same raw identifier under a
 * different provider/realm is a different binding and never auto-merges accounts. Credential material
 * (password hash, WebAuthn public key, provider token) is NOT stored here — it lives in dedicated credential
 * storage referenced by the binding.
 */
public record LoginIdentity(
        UUID identityId,
        UUID accountId,
        LoginIdentityType type,
        String providerAuthority,
        String normalizedIdentifier,
        LoginIdentityStatus status,
        Instant verifiedAt,
        Instant linkedAt,
        @Nullable Instant lastUsedAt) {

    public LoginIdentity {
        Objects.requireNonNull(identityId, "identityId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(providerAuthority, "providerAuthority");
        Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(linkedAt, "linkedAt");
        if (providerAuthority.isBlank() || normalizedIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "providerAuthority and normalizedIdentifier must be non-blank");
        }
    }

    /** Whether this binding is usable for authentication (its own status; the account epoch is checked separately). */
    public boolean isActive() {
        return status == LoginIdentityStatus.ACTIVE;
    }

    /** Whether this binding has been used at least once since being linked. */
    public boolean hasBeenUsed() {
        return lastUsedAt != null;
    }
}
