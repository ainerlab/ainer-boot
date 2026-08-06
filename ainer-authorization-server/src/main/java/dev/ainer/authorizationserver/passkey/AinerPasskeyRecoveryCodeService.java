package dev.ainer.authorizationserver.passkey;

import dev.ainer.core.error.BusinessException;
import dev.ainer.web.request.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Passkey 恢复码签发与自助赎回。见 ADR-0015。
 *
 * <p>签发：在已验证的浏览器会话里生成一组高熵一次性码，明文只返回一次，数据库只保存 bcrypt
 * 哈希；新签发会令上一组 ACTIVE 码失效。赎回：密码登录本人提交一枚明文码，校验通过则吊销该
 * subject 全部 ACTIVE Passkey（越过最后凭证保护），用户随后可重新 bootstrap。
 *
 * <p>失败次数按 subject 累计；超过上限后该组恢复码锁定，只能走管理员双人恢复。
 */
public final class AinerPasskeyRecoveryCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_COUNT = 8;
    private static final int CODE_BYTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    // 去掉易混字符（0/O/1/I/L）的 32 字符表
    private static final char[] ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AinerJdbcPasskeyCredentialRepository credentialRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AinerPasskeyRecoveryCodeService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AinerJdbcPasskeyCredentialRepository credentialRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.credentialRepository = credentialRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public RecoveryCodeIssuance issue(UUID tenantId, UUID subjectId) {
        Assert.notNull(tenantId, "tenantId cannot be null");
        Assert.notNull(subjectId, "subjectId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            UUID operationId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_code
                    SET status = 'SUPERSEDED', used_at = ?
                    WHERE subject_id = ? AND status = 'ACTIVE'
                    """,
                    Timestamp.from(now), subjectId);
            List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
            for (int i = 0; i < CODE_COUNT; i++) {
                String plaintext = generateCode();
                plaintextCodes.add(plaintext);
                jdbcTemplate.update(
                        """
                        INSERT INTO ainer_passkey_recovery_code(
                            id, subject_id, tenant_id, code_hash, status, issued_at, used_at
                        ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, NULL)
                        """,
                        UUID.randomUUID(), subjectId, tenantId,
                        passwordEncoder.encode(plaintext), Timestamp.from(now));
            }
            securityAudit(operationId, tenantId, subjectId,
                    "RECOVERY_CODE_ISSUED", "ISSUED", "USER", subjectId.toString(), null, now);
            return new RecoveryCodeIssuance(operationId, List.copyOf(plaintextCodes));
        });
    }

    /**
     * 赎回一枚明文恢复码。成功返回 true（已吊销全部 ACTIVE Passkey 并写审计）；
     * 码不匹配返回 false；锁定中抛 {@link PasskeyErrorCode#RECOVERY_LOCKED_OUT}。
     */
    public boolean redeem(UUID tenantId, UUID subjectId, String plaintextCode) {
        Assert.notNull(subjectId, "subjectId cannot be null");
        Assert.hasText(plaintextCode, "plaintextCode cannot be empty");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (isLocked(subjectId, now)) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_LOCKED_OUT);
            }
            List<RecoveryCodeRow> activeCodes = jdbcTemplate.query(
                    """
                    SELECT id, code_hash
                    FROM ainer_passkey_recovery_code
                    WHERE subject_id = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new RecoveryCodeRow(
                            resultSet.getString("id"), resultSet.getString("code_hash")),
                    subjectId);
            UUID matchedId = null;
            for (RecoveryCodeRow code : activeCodes) {
                if (passwordEncoder.matches(plaintextCode, code.codeHash())) {
                    matchedId = UUID.fromString(code.id());
                    break;
                }
            }
            if (matchedId == null) {
                registerFailedAttempt(tenantId, subjectId, now);
                return false;
            }
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_code
                    SET status = 'USED', used_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """,
                    Timestamp.from(now), matchedId);
            // 成功后清空失败计数
            jdbcTemplate.update(
                    "DELETE FROM ainer_passkey_recovery_lockout WHERE subject_id = ?", subjectId);
            int revoked = credentialRepository.revokeAllActiveForSubject(subjectId);
            UUID operationId = UUID.randomUUID();
            securityAudit(operationId, tenantId, subjectId,
                    "SELF_RECOVERY", "REDEEMED", "USER", subjectId.toString(), null, now);
            return true;
        });
    }

    public RecoveryCodeIssuance issueForAccount(UUID accountId) {
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            UUID operationId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_code
                    SET status = 'SUPERSEDED', used_at = ?
                    WHERE account_id = ? AND status = 'ACTIVE'
                    """,
                    Timestamp.from(now), accountId);
            List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
            for (int i = 0; i < CODE_COUNT; i++) {
                String plaintext = generateCode();
                plaintextCodes.add(plaintext);
                jdbcTemplate.update(
                        """
                        INSERT INTO ainer_passkey_recovery_code(
                            id, subject_id, account_id, tenant_id, code_hash, status, issued_at, used_at
                        ) VALUES (?, NULL, ?, NULL, ?, 'ACTIVE', ?, NULL)
                        """,
                        UUID.randomUUID(), accountId,
                        passwordEncoder.encode(plaintext), Timestamp.from(now));
            }
            securityAuditForAccount(
                    operationId, accountId, "RECOVERY_CODE_ISSUED", "ISSUED", "USER",
                    accountId.toString(), null, now);
            return new RecoveryCodeIssuance(operationId, List.copyOf(plaintextCodes));
        });
    }

    public boolean redeemForAccount(UUID accountId, String plaintextCode) {
        Assert.notNull(accountId, "accountId cannot be null");
        Assert.hasText(plaintextCode, "plaintextCode cannot be empty");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (isLockedForAccount(accountId, now)) {
                throw new BusinessException(PasskeyErrorCode.RECOVERY_LOCKED_OUT);
            }
            List<RecoveryCodeRow> activeCodes = jdbcTemplate.query(
                    """
                    SELECT id, code_hash
                    FROM ainer_passkey_recovery_code
                    WHERE account_id = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """,
                    (resultSet, rowNumber) -> new RecoveryCodeRow(
                            resultSet.getString("id"), resultSet.getString("code_hash")),
                    accountId);
            UUID matchedId = null;
            for (RecoveryCodeRow code : activeCodes) {
                if (passwordEncoder.matches(plaintextCode, code.codeHash())) {
                    matchedId = UUID.fromString(code.id());
                    break;
                }
            }
            if (matchedId == null) {
                registerFailedAttemptForAccount(accountId, now);
                return false;
            }
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_code
                    SET status = 'USED', used_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """,
                    Timestamp.from(now), matchedId);
            jdbcTemplate.update(
                    "DELETE FROM ainer_passkey_recovery_lockout WHERE account_id = ?", accountId);
            int revoked = credentialRepository.revokeAllActiveForAccount(accountId);
            UUID operationId = UUID.randomUUID();
            securityAuditForAccount(
                    operationId, accountId, "SELF_RECOVERY", "REDEEMED", "USER",
                    accountId.toString(), null, now);
            return true;
        });
    }

    private boolean isLocked(UUID subjectId, Instant now) {
        List<Timestamp> lockedUntil = jdbcTemplate.query(
                "SELECT locked_until FROM ainer_passkey_recovery_lockout WHERE subject_id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp("locked_until"),
                subjectId);
        return !lockedUntil.isEmpty()
                && lockedUntil.getFirst() != null
                && lockedUntil.getFirst().toInstant().isAfter(now);
    }

    private boolean isLockedForAccount(UUID accountId, Instant now) {
        List<Timestamp> lockedUntil = jdbcTemplate.query(
                "SELECT locked_until FROM ainer_passkey_recovery_lockout WHERE account_id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp("locked_until"),
                accountId);
        return !lockedUntil.isEmpty()
                && lockedUntil.getFirst() != null
                && lockedUntil.getFirst().toInstant().isAfter(now);
    }

    private void registerFailedAttempt(UUID tenantId, UUID subjectId, Instant now) {
        Integer attempts = jdbcTemplate.queryForObject(
                """
                INSERT INTO ainer_passkey_recovery_lockout(
                    subject_id, tenant_id, failed_attempts, locked_until, updated_at
                ) VALUES (?, ?, 1, NULL, ?)
                ON CONFLICT (subject_id) WHERE subject_id IS NOT NULL DO UPDATE
                    SET failed_attempts = ainer_passkey_recovery_lockout.failed_attempts + 1,
                        updated_at = EXCLUDED.updated_at
                RETURNING failed_attempts
                """,
                Integer.class, subjectId, tenantId, Timestamp.from(now));
        if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_lockout
                    SET locked_until = ?, updated_at = ?
                    WHERE subject_id = ? AND locked_until IS NULL
                    """,
                    Timestamp.from(now.plus(LOCKOUT_DURATION)), Timestamp.from(now), subjectId);
        }
    }

    private void registerFailedAttemptForAccount(UUID accountId, Instant now) {
        Integer attempts = jdbcTemplate.queryForObject(
                """
                INSERT INTO ainer_passkey_recovery_lockout(
                    subject_id, account_id, tenant_id, failed_attempts, locked_until, updated_at
                ) VALUES (NULL, ?, NULL, 1, NULL, ?)
                ON CONFLICT (account_id) WHERE account_id IS NOT NULL DO UPDATE
                    SET failed_attempts = ainer_passkey_recovery_lockout.failed_attempts + 1,
                        updated_at = EXCLUDED.updated_at
                RETURNING failed_attempts
                """,
                Integer.class, accountId, Timestamp.from(now));
        if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_lockout
                    SET locked_until = ?, updated_at = ?
                    WHERE account_id = ? AND locked_until IS NULL
                    """,
                    Timestamp.from(now.plus(LOCKOUT_DURATION)), Timestamp.from(now), accountId);
        }
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

    private void securityAuditForAccount(
            UUID operationId, UUID accountId, String operationType, String phase,
            String actorType, String actorId, String incidentReference, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, tenant_id, subject_id, account_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), operationId, accountId, operationType, phase,
                actorType, actorId, incidentReference, MDC.get(RequestIdFilter.MDC_KEY),
                Timestamp.from(occurredAt));
    }

    private static String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(CODE_BYTES + 1);
        for (int i = 0; i < CODE_BYTES; i++) {
            if (i == 5) {
                builder.append('-');
            }
            builder.append(ALPHABET[(bytes[i] & 0xFF) % ALPHABET.length]);
        }
        return builder.toString();
    }

    public record RecoveryCodeIssuance(UUID operationId, List<String> plaintextCodes) {
    }

    private record RecoveryCodeRow(String id, String codeHash) {
    }
}
