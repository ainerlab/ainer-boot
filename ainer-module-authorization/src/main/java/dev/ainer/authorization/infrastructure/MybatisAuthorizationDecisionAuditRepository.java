package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.AuthorizationDecisionAudit;
import dev.ainer.authorization.application.AuthorizationDecisionAuditRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link AuthorizationDecisionAuditRepository} 的 MyBatis 实现（ADR-0030 §12.4）。
 */
@Repository
public class MybatisAuthorizationDecisionAuditRepository implements AuthorizationDecisionAuditRepository {

    private final AuthorizationDecisionAuditMapper mapper;

    public MybatisAuthorizationDecisionAuditRepository(AuthorizationDecisionAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AuthorizationDecisionAudit audit) {
        AuthorizationDecisionAuditRow row = new AuthorizationDecisionAuditRow();
        row.setDecisionId(audit.decisionId());
        row.setWorkspaceId(audit.workspaceId());
        row.setRequesterIssuer(audit.requesterIssuer());
        row.setRequesterType(audit.requesterType());
        row.setRequesterId(audit.requesterId());
        row.setPermissionCode(audit.permissionCode());
        row.setResourceType(audit.resourceType());
        row.setResourceId(audit.resourceId());
        row.setOutcome(audit.outcome().name());
        row.setReasonCode(audit.reasonCode());
        row.setPolicyVersion(audit.policyVersion());
        row.setRequestId(audit.requestId());
        row.setTraceId(audit.traceId());
        row.setEvaluatedAt(audit.evaluatedAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException(
                    "Authorization decision audit insert affected an unexpected number of rows");
        }
    }
}
