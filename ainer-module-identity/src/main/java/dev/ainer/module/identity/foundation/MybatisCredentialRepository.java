package dev.ainer.module.identity.foundation;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link CredentialRepository} (Greenfield foundation persistence, S2).
 * Mirrors the project's plain-MyBatis repository style: delegates to {@link CredentialMapper}, generates
 * primary keys via {@code uuidv7()} and fails loudly when an insert does not affect exactly one row.
 * {@link #revokeActive} returns whether an ACTIVE material was actually superseded.
 */
@Repository
public class MybatisCredentialRepository implements CredentialRepository {

    private final CredentialMapper mapper;

    public MybatisCredentialRepository(CredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void insert(Credential credential) {
        CredentialRow row = new CredentialRow();
        row.setId(credential.credentialId());
        row.setAccountId(credential.accountId());
        row.setType(credential.type().name());
        row.setCredentialData(credential.credentialData());
        row.setStatus(credential.status().name());
        row.setCreatedAt(credential.createdAt());
        if (mapper.insertCredential(row) != 1) {
            throw new IllegalStateException(
                    "failed to insert Credential " + credential.credentialId());
        }
    }

    @Override
    public Optional<Credential> findActive(UUID accountId, CredentialType type) {
        return Optional.ofNullable(mapper.selectActiveByAccountAndType(accountId, type.name()))
                .map(MybatisCredentialRepository::toCredential);
    }

    @Override
    public int revokeActive(UUID accountId, CredentialType type, Instant rotatedAt) {
        return mapper.revokeActiveByAccountAndType(accountId, type.name(), rotatedAt);
    }

    private static Credential toCredential(CredentialRow row) {
        return new Credential(
                row.getId(),
                row.getAccountId(),
                CredentialType.valueOf(row.getType()),
                row.getCredentialData(),
                CredentialStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                row.getRotatedAt());
    }
}