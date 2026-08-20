package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把授权决策记录到 append-only 决策审计（ADR-0030 §12.4）。
 *
 * <p>与在管理变更事务内调用的 {@link AuthorizationChangeAuditService} 不同，决策审计写入
 * 使用 {@code REQUIRES_NEW}：决策本身是纯逻辑，不属于业务事务；即使调用方随后抛出异常，
 * DENY 也必须被记录。调用方（应用服务或 Spring Security 适配器）根据触发权限的
 * {@code Permission.auditLevel} 决定是否记录——并非每次读取都审计。
 *
 * <p>{@link AuthorizationService} 保持无 Spring 且不直接调用本服务。调用方在收到
 * {@link AuthorizationDecision} 后自行调用 {@link #recordIfApplicable}。
 */
@Service
public class AuthorizationDecisionAuditService {

    private final AuthorizationDecisionAuditRepository repository;

    public AuthorizationDecisionAuditService(AuthorizationDecisionAuditRepository repository) {
        this.repository = repository;
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
}
