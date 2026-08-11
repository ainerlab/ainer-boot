package dev.ainer.authorization.application;

import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Records authorization catalog management actions to the append-only change audit
 * (ADR-0030 §11.7, §12.4). Writes are issued in the caller's transaction so that an audit failure
 * rolls back the business change ("审计失败则回滚"); this differs from the workspace decision-audit
 * pattern which uses {@code REQUIRES_NEW} to survive a denied-operation rollback.
 *
 * <p>The actor is derived from the {@link AuthenticatedPrincipal} that authorized the management
 * operation. No Token, credential, prompt or resource body is stored — only stable identity
 * references, target, action, version deltas and trace ids.
 */
@Service
public class AuthorizationChangeAuditService {

    private final AuthorizationChangeAuditRepository repository;
    private final Clock clock;

    public AuthorizationChangeAuditService(AuthorizationChangeAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Record a management change. Called within the same transaction as the Role/Binding mutation.
     *
     * @param actor         the principal that authorized the management operation
     * @param targetType    {@code "ROLE"} or {@code "BINDING"}
     * @param targetId      primary key of the changed target
     * @param action        {@code "CREATE"}, {@code "REPLACE_PERMISSIONS"}, {@code "REVOKE"}
     * @param beforeVersion target version before the change, or null for create
     * @param afterVersion  target version after the change, or null
     * @param requestId     request trace id, or null
     * @param traceId       distributed trace id, or null
     */
    @Transactional
    public void record(
            AuthenticatedPrincipal actor,
            String targetType,
            UUID targetId,
            String action,
            @Nullable Long beforeVersion,
            @Nullable Long afterVersion,
            @Nullable String requestId,
            @Nullable String traceId) {
        repository.insert(new AuthorizationChangeAudit(
                null,
                actor.authority().issuer(),
                actor.isService() ? "SERVICE" : "USER",
                actor.subjectId(),
                targetType,
                targetId,
                action,
                beforeVersion,
                afterVersion,
                requestId,
                traceId,
                clock.instant()));
    }
}
