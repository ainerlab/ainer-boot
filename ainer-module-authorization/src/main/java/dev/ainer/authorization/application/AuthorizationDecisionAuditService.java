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
 * Records authorization decisions to the append-only decision audit (ADR-0030 §12.4).
 *
 * <p>Unlike {@link AuthorizationChangeAuditService}, which is called within the management mutation
 * transaction, decision audit writes use {@code REQUIRES_NEW}: the decision itself is pure logic and
 * not part of a business transaction; a DENY must be recorded even if the caller later throws. The
 * caller (application service or Spring Security adapter) decides whether to record based on the
 * triggering {@code Permission.auditLevel} — not every read is audited.
 *
 * <p>{@link AuthorizationService} stays Spring-free and does not call this service directly. The
 * caller invokes {@link #recordIfApplicable} after receiving the {@link AuthorizationDecision}.
 */
@Service
public class AuthorizationDecisionAuditService {

    private final AuthorizationDecisionAuditRepository repository;

    public AuthorizationDecisionAuditService(AuthorizationDecisionAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Record a decision for an authenticated request. Anonymous/PUBLIC decisions are not recorded
     * here (the decision_audit table requires non-null requester fields; PUBLIC audit is handled
     * separately per ADR §12.4).
     *
     * @param request  the original authorization request
     * @param decision the decision returned by {@link dev.ainer.authorization.AuthorizationService}
     * @param requestId request trace id, or null
     * @param traceId   distributed trace id, or null
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
