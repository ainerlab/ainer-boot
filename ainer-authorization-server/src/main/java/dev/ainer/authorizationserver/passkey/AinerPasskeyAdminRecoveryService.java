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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Two-person Passkey recovery for one HumanAccount. */
public final class AinerPasskeyAdminRecoveryService {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final String REQUESTED = "REQUESTED";
    private static final String EXECUTED = "EXECUTED";
    private static final String EXPIRED = "EXPIRED";

    private final JdbcTemplate jdbcTemplate;
    private final AinerJdbcPasskeyCredentialRepository credentialRepository;
    private final HumanAccountRepository humanAccountRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerPasskeyAdminRecoveryService(
            JdbcTemplate jdbcTemplate,
            AinerJdbcPasskeyCredentialRepository credentialRepository,
            HumanAccountRepository humanAccountRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.credentialRepository = credentialRepository;
        this.humanAccountRepository = humanAccountRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public AccountRecoveryRequest requestRecoveryForAccount(
            String requesterServiceId, UUID accountId, String incidentReference, Duration approvalTtl) {
        requireSafe(requesterServiceId, "requesterServiceId");
        requireSafe(incidentReference, "incidentReference");
        requireTtl(approvalTtl);
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            requireActiveAccount(accountId);
            Instant now = clock.instant();
            expireOpenRequests(accountId, now);
            requireAccountHasActivePasskey(accountId);
            UUID requestId = nextUuidV7();
            Instant expiresAt = now.plus(approvalTtl);
            jdbcTemplate.update(
                    """
                    INSERT INTO ainer_passkey_recovery_request(
                        id, account_id, requested_by, approved_by, incident_reference,
                        status, requested_at, expires_at, executed_at
                    ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, NULL)
                    """,
                    requestId, accountId, requesterServiceId, incidentReference,
                    REQUESTED, Timestamp.from(now), Timestamp.from(expiresAt));
            securityAudit(requestId, accountId, "ADMIN_RECOVERY", REQUESTED,
                    requesterServiceId, incidentReference, now);
            return new AccountRecoveryRequest(
                    requestId, accountId, requesterServiceId, null, incidentReference,
                    REQUESTED, now, expiresAt, null);
        });
    }

    public AccountRecoveryRequest approveAndExecuteForAccount(
            String approverServiceId, UUID accountId, UUID requestId) {
        requireSafe(approverServiceId, "approverServiceId");
        Assert.notNull(accountId, "accountId cannot be null");
        Assert.notNull(requestId, "requestId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<AccountRecoveryRequest> requests = jdbcTemplate.query(
                    """
                    SELECT id, account_id, requested_by, approved_by, incident_reference,
                           status, requested_at, expires_at, executed_at
                    FROM ainer_passkey_recovery_request
                    WHERE account_id = ? AND id = ?
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new AccountRecoveryRequest(
                            UUID.fromString(resultSet.getString("id")),
                            resultSet.getObject("account_id", UUID.class),
                            resultSet.getString("requested_by"), resultSet.getString("approved_by"),
                            resultSet.getString("incident_reference"), resultSet.getString("status"),
                            resultSet.getTimestamp("requested_at").toInstant(),
                            resultSet.getTimestamp("expires_at").toInstant(),
                            resultSet.getTimestamp("executed_at") == null
                                    ? null : resultSet.getTimestamp("executed_at").toInstant()),
                    accountId, requestId);
            if (requests.isEmpty()) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_NOT_FOUND);
            }
            AccountRecoveryRequest request = requests.getFirst();
            if (!REQUESTED.equals(request.status())) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_CONFLICT);
            }
            if (request.requestedBy().equals(approverServiceId)) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_APPROVER_MUST_DIFFER);
            }
            if (!now.isBefore(request.expiresAt())) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_EXPIRED);
            }
            requireActiveAccount(accountId);
            requireAccountHasActivePasskey(accountId);
            credentialRepository.revokeAllActiveForAccount(accountId);
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_request
                    SET status = ?, approved_by = ?, executed_at = ?
                    WHERE id = ? AND account_id = ? AND status = 'REQUESTED'
                      AND expires_at > ? AND requested_by <> ?
                    """,
                    EXECUTED, approverServiceId, Timestamp.from(now), requestId,
                    accountId, Timestamp.from(now), approverServiceId);
            if (changed != 1) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_CONFLICT);
            }
            securityAudit(requestId, accountId, "ADMIN_RECOVERY", EXECUTED,
                    approverServiceId, request.incidentReference(), now);
            return new AccountRecoveryRequest(
                    requestId, accountId, request.requestedBy(), approverServiceId,
                    request.incidentReference(), EXECUTED, request.requestedAt(), request.expiresAt(), now);
        });
    }

    private void expireOpenRequests(UUID accountId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE ainer_passkey_recovery_request
                SET status = ?
                WHERE account_id = ? AND status = 'REQUESTED' AND expires_at <= ?
                """,
                EXPIRED, accountId, Timestamp.from(now));
    }

    private void requireActiveAccount(UUID accountId) {
        if (humanAccountRepository.findByAccountId(accountId)
                .filter(account -> account.status().canAuthenticate())
                .isEmpty()) {
            throw new BusinessException(PasskeyErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void requireAccountHasActivePasskey(UUID accountId) {
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_passkey_credential WHERE account_id = ? AND status = 'ACTIVE'",
                Integer.class, accountId);
        if (activeCount == null || activeCount == 0) {
            throw new BusinessException(PasskeyErrorCode.RECOVERY_NOT_REQUIRED);
        }
    }

    private void securityAudit(
            UUID operationId, UUID accountId, String operationType, String phase,
            String actorId, String incidentReference, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, account_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, 'SERVICE', ?, ?, ?, ?)
                """,
                nextUuidV7(), operationId, accountId, operationType, phase, actorId,
                incidentReference, MDC.get(RequestIdFilter.MDC_KEY), Timestamp.from(occurredAt));
    }

    private UUID nextUuidV7() {
        return jdbcTemplate.queryForObject("SELECT uuidv7()", UUID.class);
    }

    private static void requireSafe(String value, String name) {
        if (value == null || !SAFE_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.toHours() > 24) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    public record AccountRecoveryRequest(
            UUID id, UUID accountId, String requestedBy, String approvedBy,
            String incidentReference, String status,
            Instant requestedAt, Instant expiresAt, Instant executedAt) {
    }
}
