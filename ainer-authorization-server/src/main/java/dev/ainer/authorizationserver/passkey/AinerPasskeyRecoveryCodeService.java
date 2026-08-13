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

/** Account-scoped Passkey recovery codes. Plaintext is returned only at issuance. */
public final class AinerPasskeyRecoveryCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_COUNT = 8;
    private static final int CODE_BYTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

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

    public RecoveryCodeIssuance issueForAccount(UUID accountId) {
        Assert.notNull(accountId, "accountId cannot be null");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            UUID operationId = nextUuidV7();
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
                            id, account_id, code_hash, status, issued_at, used_at
                        ) VALUES (?, ?, ?, 'ACTIVE', ?, NULL)
                        """,
                        nextUuidV7(), accountId, passwordEncoder.encode(plaintext), Timestamp.from(now));
            }
            securityAudit(operationId, accountId, "RECOVERY_CODE_ISSUED", "ISSUED", "USER", now);
            return new RecoveryCodeIssuance(operationId, List.copyOf(plaintextCodes));
        });
    }

    public boolean redeemForAccount(UUID accountId, String plaintextCode) {
        Assert.notNull(accountId, "accountId cannot be null");
        Assert.hasText(plaintextCode, "plaintextCode cannot be empty");
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (isLocked(accountId, now)) {
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
                registerFailedAttempt(accountId, now);
                return false;
            }
            jdbcTemplate.update(
                    """
                    UPDATE ainer_passkey_recovery_code
                    SET status = 'USED', used_at = ?
                    WHERE id = ? AND status = 'ACTIVE'
                    """,
                    Timestamp.from(now), matchedId);
            jdbcTemplate.update("DELETE FROM ainer_passkey_recovery_lockout WHERE account_id = ?", accountId);
            credentialRepository.revokeAllActiveForAccount(accountId);
            securityAudit(nextUuidV7(), accountId, "SELF_RECOVERY", "REDEEMED", "USER", now);
            return true;
        });
    }

    private boolean isLocked(UUID accountId, Instant now) {
        List<Timestamp> lockedUntil = jdbcTemplate.query(
                "SELECT locked_until FROM ainer_passkey_recovery_lockout WHERE account_id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp("locked_until"), accountId);
        return !lockedUntil.isEmpty()
                && lockedUntil.getFirst() != null
                && lockedUntil.getFirst().toInstant().isAfter(now);
    }

    private void registerFailedAttempt(UUID accountId, Instant now) {
        Integer attempts = jdbcTemplate.queryForObject(
                """
                INSERT INTO ainer_passkey_recovery_lockout(
                    account_id, failed_attempts, locked_until, updated_at
                ) VALUES (?, 1, NULL, ?)
                ON CONFLICT (account_id) DO UPDATE
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
            UUID operationId, UUID accountId, String operationType, String phase,
            String actorType, Instant occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ainer_passkey_security_operation_audit(
                    id, operation_id, account_id, operation_type, phase,
                    actor_type, actor_id, incident_reference, request_id, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """,
                nextUuidV7(), operationId, accountId, operationType, phase, actorType,
                accountId.toString(), MDC.get(RequestIdFilter.MDC_KEY), Timestamp.from(occurredAt));
    }

    private UUID nextUuidV7() {
        return jdbcTemplate.queryForObject("SELECT uuidv7()", UUID.class);
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
