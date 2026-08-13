package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.foundation.HumanAccount;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentity;
import dev.ainer.module.identity.foundation.LoginIdentityType;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Account-bound lifecycle adapter around Spring Security's WebAuthn JDBC repository. */
public final class AinerJdbcPasskeyCredentialRepository implements UserCredentialRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcUserCredentialRepository delegate;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final IdentityFoundationService foundationService;
    private final HumanAccountRepository humanAccountRepository;
    private final String foundationIssuer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean requireEnrollmentGrant;

    public AinerJdbcPasskeyCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityFoundationService foundationService,
            HumanAccountRepository humanAccountRepository,
            String foundationIssuer,
            PlatformTransactionManager transactionManager,
            Clock clock,
            boolean requireEnrollmentGrant) {
        this.jdbcTemplate = jdbcTemplate;
        this.delegate = new JdbcUserCredentialRepository(jdbcTemplate);
        this.userEntities = userEntities;
        this.foundationService = foundationService;
        this.humanAccountRepository = humanAccountRepository;
        this.foundationIssuer = foundationIssuer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.requireEnrollmentGrant = requireEnrollmentGrant;
    }

    @Override
    public void delete(Bytes credentialId) {
        Assert.notNull(credentialId, "credentialId cannot be null");
        transactionTemplate.executeWithoutResult(status -> revoke(credentialId));
    }

    /** Revoke every active credential for an account inside the recovery transaction. */
    public int revokeAllActiveForAccount(UUID accountId) {
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            List<ActiveCredential> active = jdbcTemplate.query(
                    """
                    SELECT credential_id, user_entity_user_id
                    FROM ainer_passkey_credential
                    WHERE account_id = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new ActiveCredential(
                            resultSet.getString("credential_id"),
                            resultSet.getString("user_entity_user_id")),
                    accountId);
            if (active.isEmpty()) {
                return 0;
            }
            Instant now = clock.instant();
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_credential
                    SET status = ?, revoked_at = ?, updated_at = ?, version = version + 1
                    WHERE account_id = ? AND status = 'ACTIVE'
                    """,
                    REVOKED, Timestamp.from(now), Timestamp.from(now), accountId);
            for (ActiveCredential credential : active) {
                audit(credential.credentialId(), credential.userEntityUserId(), accountId, REVOKED, now);
            }
            return changed;
        });
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        Assert.notNull(credentialRecord, "credentialRecord cannot be null");
        transactionTemplate.executeWithoutResult(status -> {
            CredentialRecord existing = delegate.findByCredentialId(credentialRecord.getCredentialId());
            if (existing == null) {
                register(credentialRecord);
                return;
            }
            if (!existing.getUserEntityUserId().equals(credentialRecord.getUserEntityUserId())) {
                throw new IllegalStateException("Ainer passkey credential cannot be reassigned");
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
                    .filter(record -> activeIds.contains(record.getCredentialId().toBase64UrlString()))
                    .toList();
        });
    }

    private void register(CredentialRecord credentialRecord) {
        String credentialId = credentialRecord.getCredentialId().toBase64UrlString();
        Integer existingLifecycle = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_passkey_credential WHERE credential_id = ?",
                Integer.class, credentialId);
        if (existingLifecycle != null && existingLifecycle > 0) {
            throw new IllegalStateException("Passkey lifecycle exists without its protocol credential");
        }
        PublicKeyCredentialUserEntity userEntity = userEntities.findById(credentialRecord.getUserEntityUserId());
        if (userEntity == null) {
            throw new IllegalStateException("Passkey user entity does not exist");
        }
        UUID accountId = resolveAccount(userEntity.getName());
        Instant now = clock.instant();
        requireEnrollmentGrantIfFirstEnrollment(accountId, now);
        delegate.save(credentialRecord);
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential(
                    credential_id, user_entity_user_id, account_id, status,
                    registered_at, updated_at, revoked_at, version
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL, 0)
                """,
                credentialId, credentialRecord.getUserEntityUserId().toBase64UrlString(), accountId,
                Timestamp.from(now), Timestamp.from(now));
        audit(credentialId, credentialRecord.getUserEntityUserId().toBase64UrlString(),
                accountId, "REGISTERED", now);
    }

    private UUID resolveAccount(String username) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        LoginIdentity login = foundationService.findLogin(
                        LoginIdentityType.USERNAME, foundationIssuer, normalized)
                .filter(LoginIdentity::isActive)
                .orElseThrow(() -> new IllegalStateException("Passkey registration requires a login identity"));
        HumanAccount account = humanAccountRepository.findByAccountId(login.accountId())
                .filter(candidate -> candidate.status().canAuthenticate())
                .orElseThrow(() -> new IllegalStateException("Passkey registration requires an active account"));
        return account.accountId();
    }

    private void revoke(Bytes credentialId) {
        String encodedId = credentialId.toBase64UrlString();
        List<CredentialLifecycle> lifecycle = jdbcTemplate.query(
                """
                SELECT user_entity_user_id, account_id
                FROM ainer_passkey_credential
                WHERE credential_id = ? AND status = 'ACTIVE'
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new CredentialLifecycle(
                        resultSet.getString("user_entity_user_id"),
                        resultSet.getObject("account_id", UUID.class)),
                encodedId);
        if (lifecycle.isEmpty()) {
            return;
        }
        CredentialLifecycle record = lifecycle.getFirst();
        jdbcTemplate.queryForObject(
                "SELECT id FROM user_entities WHERE id = ? FOR UPDATE",
                String.class, record.userEntityUserId());
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE user_entity_user_id = ? AND status = 'ACTIVE'
                """,
                Integer.class, record.userEntityUserId());
        if (activeCount == null || activeCount <= 1) {
            throw new AccessDeniedException("Cannot remove the last active passkey");
        }
        Instant now = clock.instant();
        int changed = jdbcTemplate.update(
                """
                UPDATE ainer_passkey_credential
                SET status = ?, revoked_at = ?, updated_at = ?, version = version + 1
                WHERE credential_id = ? AND status = 'ACTIVE'
                """,
                REVOKED, Timestamp.from(now), Timestamp.from(now), encodedId);
        if (changed != 1) {
            throw new IllegalStateException("Passkey credential revocation lost its lifecycle lock");
        }
        audit(encodedId, record.userEntityUserId(), record.accountId(), REVOKED, now);
    }

    private void requireEnrollmentGrantIfFirstEnrollment(UUID accountId, Instant now) {
        if (!requireEnrollmentGrant) {
            return;
        }
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_passkey_credential WHERE account_id = ? AND status = 'ACTIVE'",
                Integer.class, accountId);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        int consumed = jdbcTemplate.update(
                """
                UPDATE ainer_passkey_enrollment_grant
                SET status = 'CONSUMED', consumed_at = ?
                WHERE account_id = ? AND status = 'ACTIVE'
                """,
                Timestamp.from(now), accountId);
        if (consumed != 1) {
            throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_REQUIRED);
        }
    }

    private void requireActive(Bytes credentialId) {
        List<String> status = jdbcTemplate.query(
                "SELECT status FROM ainer_passkey_credential WHERE credential_id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getString("status"), credentialId.toBase64UrlString());
        if (status.size() != 1 || !ACTIVE.equals(status.getFirst())) {
            throw new IllegalStateException("Passkey credential is missing or revoked");
        }
    }

    private void audit(
            String credentialId, String userEntityUserId, UUID accountId,
            String operation, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential_audit(
                    id, credential_id, user_entity_user_id, account_id,
                    operation, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                nextUuidV7(), credentialId, userEntityUserId, accountId, operation,
                MDC.get(RequestIdFilter.MDC_KEY), Timestamp.from(occurredAt));
    }

    private UUID nextUuidV7() {
        return jdbcTemplate.queryForObject("SELECT uuidv7()", UUID.class);
    }

    private record CredentialLifecycle(String userEntityUserId, UUID accountId) {
    }

    private record ActiveCredential(String credentialId, String userEntityUserId) {
    }
}
