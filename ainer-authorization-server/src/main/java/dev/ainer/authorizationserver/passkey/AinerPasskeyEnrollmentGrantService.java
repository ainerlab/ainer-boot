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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Passkey enrollment 授权控制面（默认关闭）。见 ADR-0016。操作员预登记“某 subject 允许登记首枚
 * Passkey”的授权行；首枚 Passkey 登记成功后由凭证仓库同事务置 CONSUMED。
 */
public final class AinerPasskeyEnrollmentGrantService {

    private static final String ACTIVE = "ACTIVE";
    private static final String REVOKED = "REVOKED";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerPasskeyEnrollmentGrantService(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public EnrollmentGrant grant(
            String grantedBy, UUID tenantId, UUID subjectId, String incidentReference) {
        requireSafe(grantedBy, "grantedBy");
        requireSafe(incidentReference, "incidentReference");
        Assert.notNull(tenantId, "tenantId cannot be null");
        Assert.notNull(subjectId, "subjectId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            int upserted = jdbcTemplate.update(
                    """
                    INSERT INTO ainer_passkey_enrollment_grant(
                        subject_id, tenant_id, granted_by, incident_reference, status, granted_at, consumed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NULL)
                    ON CONFLICT (subject_id) DO UPDATE
                        SET tenant_id = EXCLUDED.tenant_id,
                            granted_by = EXCLUDED.granted_by,
                            incident_reference = EXCLUDED.incident_reference,
                            status = 'ACTIVE',
                            granted_at = EXCLUDED.granted_at,
                            consumed_at = NULL
                    WHERE ainer_passkey_enrollment_grant.status <> 'ACTIVE'
                    """,
                    subjectId, tenantId, grantedBy, incidentReference, ACTIVE, Timestamp.from(now));
            if (upserted == 0) {
                throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_CONFLICT);
            }
            securityAudit(subjectId, tenantId, grantedBy, incidentReference, "GRANTED", now);
            return new EnrollmentGrant(subjectId, tenantId, grantedBy, incidentReference, ACTIVE, now, null);
        });
    }

    public EnrollmentGrant revoke(String revokedBy, UUID tenantId, UUID subjectId) {
        requireSafe(revokedBy, "revokedBy");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            int changed = jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_enrollment_grant
                    SET status = ?
                    WHERE subject_id = ? AND tenant_id = ? AND status = 'ACTIVE'
                    """,
                    REVOKED, subjectId, tenantId);
            if (changed != 1) {
                throw new BusinessException(PasskeyErrorCode.ENROLLMENT_GRANT_NOT_FOUND);
            }
            securityAudit(subjectId, tenantId, revokedBy, "revoke", "REVOKED", now);
            return new EnrollmentGrant(subjectId, tenantId, revokedBy, "revoked", REVOKED, now, null);
        });
    }

    public List<EnrollmentGrant> findGrants(UUID tenantId) {
        return jdbcTemplate.query(
                """
                SELECT subject_id, tenant_id, granted_by, incident_reference, status, granted_at, consumed_at
                FROM ainer_passkey_enrollment_grant
                WHERE tenant_id = ?
                ORDER BY granted_at DESC, subject_id
                """,
                (resultSet, rowNumber) -> new EnrollmentGrant(
                        UUID.fromString(resultSet.getString("subject_id")),
                        UUID.fromString(resultSet.getString("tenant_id")),
                        resultSet.getString("granted_by"),
                        resultSet.getString("incident_reference"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("granted_at").toInstant(),
                        resultSet.getTimestamp("consumed_at") == null
                                ? null : resultSet.getTimestamp("consumed_at").toInstant()),
                tenantId);
    }

    private void securityAudit(
            UUID subjectId, UUID tenantId, String actorServiceId,
            String incidentReference, String phase, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, tenant_id, subject_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, 'ENROLLMENT_GRANT', ?, 'SERVICE', ?, ?, ?, ?)
                """,
                UUID.randomUUID(), UUID.randomUUID(), tenantId, subjectId, phase,
                actorServiceId, incidentReference, MDC.get(RequestIdFilter.MDC_KEY),
                Timestamp.from(occurredAt));
    }

    private static void requireSafe(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._:@/-]{1,128}")) {
            throw new BusinessException(PasskeyErrorCode.INVALID_RECOVERY_REQUEST);
        }
    }

    public record EnrollmentGrant(
            UUID subjectId, UUID tenantId, String grantedBy, String incidentReference,
            String status, Instant grantedAt, Instant consumedAt) {
    }
}
