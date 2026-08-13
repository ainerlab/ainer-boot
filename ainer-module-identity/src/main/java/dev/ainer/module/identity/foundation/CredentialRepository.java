package dev.ainer.module.identity.foundation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Credential} material (ADR-0033 Greenfield §4, execution plan 缺口 A).
 *
 * <p>Keyed by account + type: at most one ACTIVE material per {@code (accountId, type)}. Rotation is
 * expressed by {@link #revokeActive} followed by {@link #save} of a fresh ACTIVE credential, never by
 * mutating the old material. The port exposes only the typed aggregate.
 */
public interface CredentialRepository {

    void insert(Credential credential);

    Optional<Credential> findActive(UUID accountId, CredentialType type);

    /** Revolves the current ACTIVE material for (account, type), if any. Returns rows affected (0 or 1). */
    int revokeActive(UUID accountId, CredentialType type, java.time.Instant rotatedAt);

    /** Next PostgreSQL UUIDv7 primary key for the credential aggregate. */
    UUID nextUuidV7();
}