package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ReasonCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 把授权决策记录到 append-only 决策审计（ADR-0037 §12.4）。
 *
 * <p>与在管理变更事务内调用的 {@link AuthorizationChangeAuditService} 不同，决策审计写入
 * 使用 {@code REQUIRES_NEW}：决策本身是纯逻辑，不属于业务事务；即使调用方随后抛出异常，
 * DENY 也必须被记录。{@code AuditLevel.NONE} 的权限（典型场景是批量公开读取）由本服务
 * 统一跳过——过滤逻辑集中在这里，调用方无需自行判断，避免元数据静默失效。
 *
 * <p>{@link AuthorizationService} 保持无 Spring 且不直接调用本服务。调用方在收到
 * {@link AuthorizationDecision} 后调用 {@link #recordIfApplicable}。
 */
@Service
public class AuthorizationDecisionAuditService {

    /** 管理面守卫拒绝统一挂靠的权限（守卫门禁语义上就是对它的 DENY 决策）。 */
    public static final String MANAGEMENT_PERMISSION = "authorization.manage";

    private static final ReasonCode MANAGER_NOT_TRUSTED = new ReasonCode("MANAGER_NOT_TRUSTED");

    private final AuthorizationDecisionAuditRepository repository;
    private final Clock clock;
    private final PermissionRegistry permissionRegistry;

    public AuthorizationDecisionAuditService(
            AuthorizationDecisionAuditRepository repository,
            Clock clock,
            PermissionRegistry permissionRegistry) {
        this.repository = repository;
        this.clock = clock;
        this.permissionRegistry = permissionRegistry;
    }

    /**
     * 记录一次已认证请求的决策。匿名/PUBLIC 决策不在此记录（decision_audit 表要求请求者
     * 字段非空；PUBLIC 审计按 ADR §12.4 单独处理）。
     *
     * @param request   原始授权请求
     * @param decision  {@link dev.ainer.authorization.AuthorizationService} 返回的决策
     * @param requestId 请求追踪 id，可为 null
     * @param traceId   分布式追踪 id，可为 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIfApplicable(
            AuthorizationRequest request, AuthorizationDecision decision,
            @Nullable String requestId, @Nullable String traceId) {
        if (!(request.requester() instanceof Requester.Authenticated subject)) {
            return;
        }
        if (auditSuppressed(request.permission())) {
            return;
        }
        ResourceRef resource = request.resource();
        repository.insert(new AuthorizationDecisionAudit(
                decision.decisionId(),
                resource.workspaceId(),
                subject.subjectRef().issuerNamespace(),
                subject.subjectRef().type().name(),
                subject.subjectRef().subjectId(),
                request.permission().value(),
                resource.resourceType().value(),
                resource.resourceId(),
                decision.outcome(),
                decision.reasonCode().value(),
                decision.policyVersion(),
                requestId,
                traceId,
                decision.evaluatedAt()));
    }

    /**
     * {@code AuditLevel.NONE} 的权限不写决策行（ADR-0037 §12.4）。未注册的权限码照常
     * 记录（fail-safe：审计缺失比审计冗余更危险）。
     */
    private boolean auditSuppressed(PermissionCode permission) {
        return permissionRegistry.find(permission)
                .map(registered -> registered.auditLevel() == AuditLevel.NONE)
                .orElse(false);
    }

    /**
     * 管理面守卫拒绝的持久化审计（{@code REQUIRES_NEW}）：把「主体未被信任执行授权管理」
     * 记录为对 {@link #MANAGEMENT_PERMISSION} 的 DENY 决策，使 fail-closed 防线被试探时
     * 有可告警、可回溯的持久化痕迹。审计写入失败时异常向调用方传播（fail-closed，
     * 不静默放行被拒操作）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordManagementDenial(
            AuthenticatedPrincipal actor,
            String policyVersion,
            @Nullable String requestId,
            @Nullable String traceId) {
        repository.insert(new AuthorizationDecisionAudit(
                Uuidv7.generate(),
                null,
                actor.authority().issuer(),
                actor.isService() ? "SERVICE" : "USER",
                actor.subjectId(),
                MANAGEMENT_PERMISSION,
                "request",
                null,
                AuthorizationOutcome.DENY,
                MANAGER_NOT_TRUSTED.value(),
                policyVersion,
                requestId,
                traceId,
                clock.instant()));
    }
}
