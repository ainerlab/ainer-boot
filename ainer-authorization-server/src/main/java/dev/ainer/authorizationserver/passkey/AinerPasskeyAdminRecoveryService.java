package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
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

/**
 * Passkey 管理员双人恢复，复刻 Workspace owner recovery 的 request/approve 骨架。见 ADR-0010/0015。
 *
 * <p>两个不同 SERVICE 身份分两阶段：申请者建立 REQUESTED，批准者在同事务 FOR UPDATE 重检条件后
 * 吊销目标 subject 全部 ACTIVE Passkey（越过最后凭证保护）。默认 15 分钟过期、至多执行一次，
 * 全程写安全操作审计。request 与 approve scope 必须授予不同 Client。
 */
public final class AinerPasskeyAdminRecoveryService {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final String REQUESTED = "REQUESTED";
    private static final String EXECUTED = "EXECUTED";
    private static final String EXPIRED = "EXPIRED";

    private final JdbcTemplate jdbcTemplate;
    private final AinerJdbcPasskeyCredentialRepository credentialRepository;
    private final AinerPasskeyTenantSubjectGuard tenantSubjectGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerPasskeyAdminRecoveryService(
            JdbcTemplate jdbcTemplate,
            AinerJdbcPasskeyCredentialRepository credentialRepository,
            AinerPasskeyTenantSubjectGuard tenantSubjectGuard,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.credentialRepository = credentialRepository;
        this.tenantSubjectGuard = tenantSubjectGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public RecoveryRequest requestRecovery(
            String requesterServiceId, UUID tenantId, UUID subjectId,
            String incidentReference, Duration approvalTtl) {
        requireSafe(requesterServiceId, "requesterServiceId");
        requireSafe(incidentReference, "incidentReference");
        requireTtl(approvalTtl);
        Assert.notNull(tenantId, "tenantId cannot be null");
        Assert.notNull(subjectId, "subjectId cannot be null");
        return transactionTemplate.execute(status -> {
            tenantSubjectGuard.requireActiveHomeTenantSubject(tenantId, subjectId);
            Instant now = clock.instant();
            expireOpenRequests(tenantId, subjectId, now);
            requireSubjectHasActivePasskey(subjectId);
            UUID requestId = UUID.randomUUID();
            Instant expiresAt = now.plus(approvalTtl);
            jdbcTemplate.update(
                    """
                    INSERT INTO ainer_passkey_recovery_request(
                        id, tenant_id, subject_id, requested_by, approved_by,
                        incident_reference, status, requested_at, expires_at, executed_at
                    ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, NULL)
                    """,
                    requestId, tenantId, subjectId, requesterServiceId,
                    incidentReference, REQUESTED, Timestamp.from(now), Timestamp.from(expiresAt));
            securityAudit(requestId, tenantId, subjectId,
                    "ADMIN_RECOVERY", REQUESTED, "SERVICE", requesterServiceId, incidentReference, now);
            return new RecoveryRequest(requestId, tenantId, subjectId, requesterServiceId,
                    null, incidentReference, REQUESTED, now, expiresAt, null);
        });
    }

    public RecoveryRequest approveAndExecute(
            String approverServiceId, UUID tenantId, UUID requestId) {
        requireSafe(approverServiceId, "approverServiceId");
        Assert.notNull(tenantId, "tenantId cannot be null");
        Assert.notNull(requestId, "requestId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<RecoveryRequest> requests = jdbcTemplate.query(
                    """
                    SELECT id, tenant_id, subject_id, requested_by, approved_by,
                           incident_reference, status, requested_at, expires_at, executed_at
                    FROM ainer_passkey_recovery_request
                    WHERE tenant_id = ? AND id = ?
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> mapRequest(resultSet),
                    tenantId, requestId);
            if (requests.isEmpty()) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_NOT_FOUND);
            }
            RecoveryRequest request = requests.getFirst();
            if (!REQUESTED.equals(request.status())) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_CONFLICT);
            }
            if (request.requestedBy().equals(approverServiceId)) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_APPROVER_MUST_DIFFER);
            }
            if (!now.isBefore(request.expiresAt())) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_EXPIRED);
            }
            tenantSubjectGuard.requireActiveHomeTenantSubject(tenantId, request.subjectId());
            requireSubjectHasActivePasskey(request.subjectId());
            int revoked = credentialRepository.revokeAllActiveForSubject(request.subjectId());
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_request
                    SET status = ?, approved_by = ?, executed_at = ?
                    WHERE id = ? AND status = 'REQUESTED' AND expires_at > ?
                      AND requested_by <> ?
                    """,
                    EXECUTED, approverServiceId, Timestamp.from(now),
                    requestId, Timestamp.from(now), approverServiceId);
            if (changed != 1) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_REQUEST_CONFLICT);
            }
            securityAudit(requestId, tenantId, request.subjectId(),
                    "ADMIN_RECOVERY", EXECUTED, "SERVICE", approverServiceId,
                    request.incidentReference(), now);
            return new RecoveryRequest(requestId, tenantId, request.subjectId(),
                    request.requestedBy(), approverServiceId, request.incidentReference(),
                    EXECUTED, request.requestedAt(), request.expiresAt(), now);
        });
    }

    private void expireOpenRequests(UUID tenantId, UUID subjectId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE ainer_passkey_recovery_request
                SET status = ?
                WHERE tenant_id = ? AND subject_id = ? AND status = 'REQUESTED' AND expires_at <= ?
                """,
                EXPIRED, tenantId, subjectId, Timestamp.from(now));
    }

    private void requireSubjectHasActivePasskey(UUID subjectId) {
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ainer_passkey_credential
                WHERE subject_id = ? AND status = 'ACTIVE'
                """,
                Integer.class, subjectId);
        if (activeCount == null || activeCount == 0) {
            throw new BusinessException(PasskeyErrorCode.RECOVERY_NOT_REQUIRED);
        }
    }

    private RecoveryRequest mapRequest(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new RecoveryRequest(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("tenant_id")),
                UUID.fromString(resultSet.getString("subject_id")),
                resultSet.getString("requested_by"),
                resultSet.getString("approved_by"),
                resultSet.getString("incident_reference"),
                resultSet.getString("status"),
                resultSet.getTimestamp("requested_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("executed_at") == null
                        ? null : resultSet.getTimestamp("executed_at").toInstant());
    }

    private void securityAudit(
            UUID operationId, UUID tenantId, UUID subjectId,
            String operationType, String phase,
            String actorType, String actorId, String incidentReference, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, tenant_id, subject_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), operationId, tenantId, subjectId, operationType, phase,
                actorType, actorId, incidentReference, MDC.get(RequestIdFilter.MDC_KEY),
                Timestamp.from(occurredAt));
    }

    private static void requireSafe(String value, String name) {
        if (value == null || !SAFE_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.toHours() > 24) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    public record RecoveryRequest(
            UUID id, UUID tenantId, UUID subjectId, String requestedBy, String approvedBy,
            String incidentReference, String status,
            Instant requestedAt, Instant expiresAt, Instant executedAt) {
    }
}
