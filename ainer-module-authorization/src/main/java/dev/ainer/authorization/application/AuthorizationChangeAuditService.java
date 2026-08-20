package dev.ainer.authorization.application;

import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 把授权目录管理动作记录到 append-only 变更审计（ADR-0030 §11.7、§12.4）。写入在调用方
 * 事务内发出，审计失败即回滚业务变更（"审计失败则回滚"）；这与 workspace 决策审计使用
 * {@code REQUIRES_NEW} 让审计在被拒操作回滚后仍存活的模式不同。
 *
 * <p>操作者来自授权该管理操作的 {@link AuthenticatedPrincipal}。不保存 Token、凭据、
 * prompt 或资源正文——只保存稳定身份引用、目标、动作、版本增量与追踪 id。
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
     * 记录一条管理变更。在 Role/Binding 变更的同一事务内调用。
     *
     * @param actor         授权该管理操作的主体
     * @param targetType    {@code "ROLE"} 或 {@code "BINDING"}
     * @param targetId      被变更目标的主键
     * @param action        {@code "CREATE"}、{@code "REPLACE_PERMISSIONS"}、{@code "REVOKE"}
     * @param beforeVersion 变更前目标版本，创建时为 null
     * @param afterVersion  变更后目标版本，可为 null
     * @param requestId     请求追踪 id，可为 null
     * @param traceId       分布式追踪 id，可为 null
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
