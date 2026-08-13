package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.web.request.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Account-bound authorization for the first Passkey enrollment. */
public final class AinerPasskeyEnrollmentGrantService {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final JdbcTemplate jdbcTemplate;
    private final HumanAccountRepository humanAccountRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerPasskeyEnrollmentGrantService(
            JdbcTemplate jdbcTemplate,
            HumanAccountRepository humanAccountRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.humanAccountRepository = humanAccountRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public AccountEnrollmentGrant grant(String grantedBy, UUID accountId, String incidentReference) {
        requireSafe(grantedBy, "grantedBy");
        requireSafe(incidentReference, "incidentReference");
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            requireActiveAccount(accountId);
            Instant now = clock.instant();
            int upserted = jdbcTemplate.update(
                    """
                    INSERT INTO ainer_passkey_enrollment_grant(
                        account_id, granted_by, incident_reference, status, granted_at, consumed_at
                    ) VALUES (?, ?, ?, ?, ?, NULL)
                    ON CONFLICT (account_id) DO UPDATE
                        SET granted_by = EXCLUDED.granted_by,
                            incident_reference = EXCLUDED.incident_reference,
                            status = 'ACTIVE', granted_at = EXCLUDED.granted_at, consumed_at = NULL
                    WHERE ainer_passkey_enrollment_grant.status <> 'ACTIVE'
                    """,
                    accountId, grantedBy, incidentReference, ACTIVE, Timestamp.from(now));
            if (upserted == 0) {
                throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_CONFLICT);
            }
            securityAudit(accountId, grantedBy, incidentReference, "GRANTED", now);
            return new AccountEnrollmentGrant(accountId, grantedBy, incidentReference, ACTIVE, now, null);
        });
    }

    public AccountEnrollmentGrant revoke(String revokedBy, UUID accountId) {
        requireSafe(revokedBy, "revokedBy");
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_enrollment_grant
                    SET status = 'REVOKED'
                    WHERE account_id = ? AND status = 'ACTIVE'
                    """,
                    accountId);
            if (changed != 1) {
                throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_NOT_FOUND);
            }
            securityAudit(accountId, revokedBy, "revoked", REVOKED, now);
            return new AccountEnrollmentGrant(accountId, revokedBy, "revoked", REVOKED, now, null);
        });
    }

    public List<AccountEnrollmentGrant> findGrantsForAccount(UUID accountId) {
        return jdbcTemplate.query(
                """
                SELECT account_id, granted_by, incident_reference, status, granted_at, consumed_at
                FROM ainer_passkey_enrollment_grant
                WHERE account_id = ?
                ORDER BY granted_at DESC
                """,
                (resultSet, rowNumber) -> new AccountEnrollmentGrant(
                        resultSet.getObject("account_id", UUID.class),
                        resultSet.getString("granted_by"),
                        resultSet.getString("incident_reference"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("granted_at").toInstant(),
                        resultSet.getTimestamp("consumed_at") == null
                                ? null : resultSet.getTimestamp("consumed_at").toInstant()),
                accountId);
    }

    private void requireActiveAccount(UUID accountId) {
        if (humanAccountRepository.findByAccountId(accountId)
                .filter(account -> account.status().canAuthenticate())
                .isEmpty()) {
            throw new BusinessException(PasskeyErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void securityAudit(
            UUID accountId, String actorServiceId, String incidentReference,
            String phase, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, account_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, ?, 'ENROLLMENT_GRANT', ?, 'SERVICE', ?, ?, ?, ?)
                """,
                nextUuidV7(), nextUuidV7(), accountId, phase, actorServiceId,
                incidentReference, MDC.get(RequestIdFilter.MDC_KEY), Timestamp.from(occurredAt));
    }

    private UUID nextUuidV7() {
        return jdbcTemplate.queryForObject("SELECT uuidv7()", UUID.class);
    }

    private static void requireSafe(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._:@/-]{1,128}")) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    public record AccountEnrollmentGrant(
            UUID accountId, String grantedBy, String incidentReference,
            String status, Instant grantedAt, Instant consumedAt) {
    }
}
