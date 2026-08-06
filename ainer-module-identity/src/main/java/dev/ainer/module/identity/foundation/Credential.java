package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Credential material bound to a {@link HumanAccount} (ADR-0033 Greenfield §4, execution plan 缺口 A).
 *
 * <p>This is the "dedicated credential storage" that {@link LoginIdentity} deliberately references without
 * storing: the opaque {@code credentialData} (password hash, WebAuthn public key reference or OIDC subject)
 * is persisted here, never in the binding. Authentication resolves a LoginIdentity to its account, then reads
 * the ACTIVE {@link CredentialType} material for that account.
 *
 * <p>Material must be rotated (never mutated): rotation marks the old ACTIVE credential {@code REVOKED} and
 * inserts a fresh ACTIVE one, so an account has at most one ACTIVE material per type at a time. The password
 * hash is encoded by the project's delegating {@code PasswordEncoder} before it reaches this record.
 */
public record Credential(
        UUID credentialId,
        UUID accountId,
        CredentialType type,
        String credentialData,
        CredentialStatus status,
        Instant createdAt,
        @Nullable Instant rotatedAt) {

    public Credential {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(credentialData, "credentialData");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (credentialData.isBlank()) {
            throw new IllegalArgumentException("credentialData must be non-blank");
        }
        if (status == CredentialStatus.ACTIVE && rotatedAt != null) {
            throw new IllegalArgumentException("ACTIVE credential must not carry a rotatedAt");
        }
    }

    /** Whether this material is usable for authentication. */
    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }
}