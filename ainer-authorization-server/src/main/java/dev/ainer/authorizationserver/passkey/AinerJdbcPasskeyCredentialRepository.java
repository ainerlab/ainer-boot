package dev.ainer.authorizationserver.passkey;

import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.web.request.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AinerJdbcPasskeyCredentialRepository implements UserCredentialRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcUserCredentialRepository delegate;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final IdentityApplicationService identityService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerJdbcPasskeyCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityApplicationService identityService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.delegate = new JdbcUserCredentialRepository(jdbcTemplate);
        this.userEntities = userEntities;
        this.identityService = identityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void delete(Bytes credentialId) {
        Assert.notNull(credentialId, "credentialId cannot be null");
        transactionTemplate.executeWithoutResult(status -> revoke(credentialId));
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        Assert.notNull(credentialRecord, "credentialRecord cannot be null");
        transactionTemplate.executeWithoutResult(status -> {
            CredentialRecord existing = delegate.findByCredentialId(
                    credentialRecord.getCredentialId());
            if (existing == null) {
                register(credentialRecord);
                return;
            }
            if (!existing.getUserEntityUserId().equals(
                    credentialRecord.getUserEntityUserId())) {
                throw new IllegalStateException(
                        "Ainer passkey credential cannot be reassigned to another user");
            }
            requireActive(credentialRecord.getCredentialId());
            delegate.save(credentialRecord);
        });
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        Assert.notNull(credentialId, "credentialId cannot be null");
        return transactionTemplate.execute(status -> {
            List<String> lifecycle = jdbcTemplate.query(
                    """
                    SELECT status
                    FROM ainer_passkey_credential
                    WHERE credential_id = ?
                    FOR SHARE
                    """,
                    (resultSet, rowNumber) -> resultSet.getString("status"),
                    credentialId.toBase64UrlString());
            if (lifecycle.size() != 1 || !ACTIVE.equals(lifecycle.getFirst())) {
                return null;
            }
            return delegate.findByCredentialId(credentialId);
        });
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        Assert.notNull(userId, "userId cannot be null");
        return transactionTemplate.execute(status -> {
            Set<String> activeIds = new HashSet<>(jdbcTemplate.query(
                    """
                    SELECT credential_id
                    FROM ainer_passkey_credential
                    WHERE user_entity_user_id = ? AND status = 'ACTIVE'
                    FOR SHARE
                    """,
                    (resultSet, rowNumber) -> resultSet.getString("credential_id"),
                    userId.toBase64UrlString()));
            if (activeIds.isEmpty()) {
                return List.of();
            }
            return delegate.findByUserId(userId).stream()
                    .filter(record -> activeIds.contains(
                            record.getCredentialId().toBase64UrlString()))
                    .toList();
        });
    }

    private void register(CredentialRecord credentialRecord) {
        String credentialId = credentialRecord.getCredentialId().toBase64UrlString();
        Integer existingLifecycle = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_passkey_credential WHERE credential_id = ?",
                Integer.class,
                credentialId);
        if (existingLifecycle != null && existingLifecycle > 0) {
            throw new IllegalStateException(
                    "Ainer passkey lifecycle exists without its protocol credential");
        }
        PublicKeyCredentialUserEntity userEntity = userEntities.findById(
                credentialRecord.getUserEntityUserId());
        if (userEntity == null) {
            throw new IllegalStateException(
                    "Ainer passkey user entity does not exist");
        }
        IdentityAccount account = identityService.findAccountByUsername(userEntity.getName())
                .filter(IdentityAccount::enabled)
                .filter(IdentityAccount::accountNonLocked)
                .orElseThrow(() -> new IllegalStateException(
                        "Ainer passkey registration requires an active identity account"));
        Instant now = clock.instant();
        delegate.save(credentialRecord);
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential(
                    credential_id, user_entity_user_id, subject_id, status,
                    registered_at, updated_at, revoked_at, version
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL, 0)
                """,
                credentialId,
                credentialRecord.getUserEntityUserId().toBase64UrlString(),
                account.subjectId(),
                Timestamp.from(now),
                Timestamp.from(now));
        audit(
                credentialId,
                credentialRecord.getUserEntityUserId().toBase64UrlString(),
                account.subjectId(),
                "REGISTERED",
                now);
    }

    private void revoke(Bytes credentialId) {
        String encodedId = credentialId.toBase64UrlString();
        List<CredentialLifecycle> lifecycle = jdbcTemplate.query(
                """
                SELECT user_entity_user_id, subject_id
                FROM ainer_passkey_credential
                WHERE credential_id = ? AND status = 'ACTIVE'
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new CredentialLifecycle(
                        resultSet.getString("user_entity_user_id"),
                        resultSet.getObject("subject_id", UUID.class)),
                encodedId);
        if (lifecycle.isEmpty()) {
            return;
        }
        CredentialLifecycle record = lifecycle.getFirst();
        jdbcTemplate.queryForObject(
                "SELECT id FROM user_entities WHERE id = ? FOR UPDATE",
                String.class,
                record.userEntityUserId());
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE user_entity_user_id = ? AND status = 'ACTIVE'
                """,
                Integer.class,
                record.userEntityUserId());
        if (activeCount == null || activeCount <= 1) {
            throw new AccessDeniedException(
                    "Ainer cannot remove the last active passkey before recovery policy is configured");
        }
        Instant now = clock.instant();
        int changed = jdbcTemplate.update(
                """
                UPDATE ainer_passkey_credential
                SET status = ?, revoked_at = ?, updated_at = ?, version = version + 1
                WHERE credential_id = ? AND status = 'ACTIVE'
                """,
                REVOKED,
                Timestamp.from(now),
                Timestamp.from(now),
                encodedId);
        if (changed != 1) {
            throw new IllegalStateException(
                    "Ainer passkey credential revocation lost its lifecycle lock");
        }
        audit(
                encodedId,
                record.userEntityUserId(),
                record.subjectId(),
                "REVOKED",
                now);
    }

    private void requireActive(Bytes credentialId) {
        List<String> status = jdbcTemplate.query(
                """
                SELECT status
                FROM ainer_passkey_credential
                WHERE credential_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getString("status"),
                credentialId.toBase64UrlString());
        if (status.size() != 1 || !ACTIVE.equals(status.getFirst())) {
            throw new IllegalStateException(
                    "Ainer passkey credential is missing lifecycle state or has been revoked");
        }
    }

    private void audit(
            String credentialId,
            String userEntityUserId,
            UUID subjectId,
            String operation,
            Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential_audit(
                    id, credential_id, user_entity_user_id, subject_id,
                    operation, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                credentialId,
                userEntityUserId,
                subjectId,
                operation,
                MDC.get(RequestIdFilter.MDC_KEY),
                Timestamp.from(occurredAt));
    }

    private record CredentialLifecycle(String userEntityUserId, UUID subjectId) {
    }
}
