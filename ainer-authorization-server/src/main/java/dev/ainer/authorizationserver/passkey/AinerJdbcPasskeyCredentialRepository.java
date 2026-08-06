package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
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

public final class AinerJdbcPasskeyCredentialRepository implements UserCredentialRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcUserCredentialRepository delegate;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final IdentityApplicationService identityService;
    private final IdentityFoundationService foundationService;
    private final HumanAccountRepository humanAccountRepository;
    private final String foundationIssuer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean requireEnrollmentGrant;

    public AinerJdbcPasskeyCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityApplicationService identityService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this(jdbcTemplate, userEntities, identityService, null, null, null,
                transactionManager, clock, false);
    }

    public AinerJdbcPasskeyCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityApplicationService identityService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            boolean requireEnrollmentGrant) {
        this(jdbcTemplate, userEntities, identityService, null, null, null,
                transactionManager, clock, requireEnrollmentGrant);
    }

    public AinerJdbcPasskeyCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityApplicationService identityService,
            IdentityFoundationService foundationService,
            HumanAccountRepository humanAccountRepository,
            String foundationIssuer,
            PlatformTransactionManager transactionManager,
            Clock clock,
            boolean requireEnrollmentGrant) {
        this.jdbcTemplate = jdbcTemplate;
        this.delegate = new JdbcUserCredentialRepository(jdbcTemplate);
        this.userEntities = userEntities;
        this.identityService = identityService;
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

    /**
     * 吊销某 subject 的全部 ACTIVE Passkey，越过“最后凭证保护”。仅在已验证的恢复上下文
     * （恢复码自助恢复或管理员双人恢复）内调用；普通自助删除仍走 {@link #delete(Bytes)}。
     * 返回实际吊销数。同事务为每枚凭证写 REVOKED 审计；恢复语义本身记在安全操作审计。
     */
    public int revokeAllActiveForSubject(UUID subjectId) {
        Assert.notNull(subjectId, "subjectId cannot be null");
        return transactionTemplate.execute(status -> {
            List<ActiveCredential> active = jdbcTemplate.query(
                    """
                    SELECT credential_id, user_entity_user_id
                    FROM ainer_passkey_credential
                    WHERE subject_id = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new ActiveCredential(
                            resultSet.getString("credential_id"),
                            resultSet.getString("user_entity_user_id")),
                    subjectId);
            if (active.isEmpty()) {
                return 0;
            }
            Instant now = clock.instant();
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_credential
                    SET status = ?, revoked_at = ?, updated_at = ?, version = version + 1
                    WHERE subject_id = ? AND status = 'ACTIVE'
                    """,
                    REVOKED,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    subjectId);
            for (ActiveCredential credential : active) {
                audit(credential.credentialId(), credential.userEntityUserId(), subjectId, null, REVOKED, now);
            }
            return changed;
        });
    }

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
                    REVOKED,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    accountId);
            for (ActiveCredential credential : active) {
                audit(credential.credentialId(), credential.userEntityUserId(), null, accountId, REVOKED, now);
            }
            return changed;
        });
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
        ResolvedOwner owner = resolveOwner(userEntity.getName());
        Instant now = clock.instant();
        if (owner.subjectId() != null) {
            requireEnrollmentGrantIfFirstEnrollment(owner.subjectId(), now);
        } else if (owner.accountId() != null) {
            requireEnrollmentGrantIfFirstEnrollmentForAccount(owner.accountId(), now);
        }
        delegate.save(credentialRecord);
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential(
                    credential_id, user_entity_user_id, subject_id, account_id, status,
                    registered_at, updated_at, revoked_at, version
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, NULL, 0)
                """,
                credentialId,
                credentialRecord.getUserEntityUserId().toBase64UrlString(),
                owner.subjectId(),
                owner.accountId(),
                Timestamp.from(now),
                Timestamp.from(now));
        audit(
                credentialId,
                credentialRecord.getUserEntityUserId().toBase64UrlString(),
                owner.subjectId(),
                owner.accountId(),
                "REGISTERED",
                now);
    }

    private ResolvedOwner resolveOwner(String username) {
        if (foundationService != null && humanAccountRepository != null && foundationIssuer != null) {
            String normalized = username.trim().toLowerCase(Locale.ROOT);
            LoginIdentity login = foundationService.findLogin(
                            LoginIdentityType.USERNAME, foundationIssuer, normalized)
                    .filter(LoginIdentity::isActive)
                    .orElse(null);
            if (login != null) {
                HumanAccount account = humanAccountRepository.findByAccountId(login.accountId())
                        .filter(candidate -> candidate.status().canAuthenticate())
                        .orElseThrow(() -> new IllegalStateException(
                                "Ainer passkey registration requires an active foundation account"));
                return new ResolvedOwner(null, account.accountId());
            }
        }
        IdentityAccount account = identityService.findAccountByUsername(username)
                .filter(IdentityAccount::enabled)
                .filter(IdentityAccount::accountNonLocked)
                .orElseThrow(() -> new IllegalStateException(
                        "Ainer passkey registration requires an active identity account"));
        return new ResolvedOwner(account.subjectId(), null);
    }

    private void revoke(Bytes credentialId) {
        String encodedId = credentialId.toBase64UrlString();
        List<CredentialLifecycle> lifecycle = jdbcTemplate.query(
                """
                SELECT user_entity_user_id, subject_id, account_id
                FROM ainer_passkey_credential
                WHERE credential_id = ? AND status = 'ACTIVE'
                FOR UPDATE
                """,
                    (resultSet, rowNumber) -> new CredentialLifecycle(
                            resultSet.getString("user_entity_user_id"),
                            resultSet.getObject("subject_id", UUID.class),
                            resultSet.getObject("account_id", UUID.class)),
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
                record.accountId(),
                "REVOKED",
                now);
    }

    private void requireEnrollmentGrantIfFirstEnrollment(UUID subjectId, Instant now) {
        if (!requireEnrollmentGrant) {
            return;
        }
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE subject_id = ? AND status = 'ACTIVE'
                """,
                Integer.class, subjectId);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        int consumed = jdbcTemplate.update(
                """
                UPDATE ainer_passkey_enrollment_grant
                SET status = 'CONSUMED', consumed_at = ?
                WHERE subject_id = ? AND status = 'ACTIVE'
                """,
                Timestamp.from(now), subjectId);
        if (consumed != 1) {
            throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_REQUIRED);
        }
    }

    private void requireEnrollmentGrantIfFirstEnrollmentForAccount(UUID accountId, Instant now) {
        if (!requireEnrollmentGrant) {
            return;
        }
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE account_id = ? AND status = 'ACTIVE'
                """,
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
            UUID accountId,
            String operation,
            Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_credential_audit(
                    id, credential_id, user_entity_user_id, subject_id, account_id,
                    operation, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                credentialId,
                userEntityUserId,
                subjectId,
                accountId,
                operation,
                MDC.get(RequestIdFilter.MDC_KEY),
                Timestamp.from(occurredAt));
    }

    private record CredentialLifecycle(String userEntityUserId, UUID subjectId, UUID accountId) {
    }

    private record ActiveCredential(String credentialId, String userEntityUserId) {
    }

    private record ResolvedOwner(UUID subjectId, UUID accountId) {
    }
}
