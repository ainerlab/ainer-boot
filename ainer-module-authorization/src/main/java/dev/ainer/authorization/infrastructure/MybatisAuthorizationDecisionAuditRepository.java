package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.AuthorizationDecisionAudit;
import dev.ainer.authorization.application.AuthorizationDecisionAuditCursor;
import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleRepository;
import dev.ainer.authorization.application.AuthorizationDecisionAuditOperationalStatus;
import dev.ainer.authorization.application.AuthorizationDecisionAuditRepository;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 决策审计的 MyBatis 实现，同时实现写入端口 {@link AuthorizationDecisionAuditRepository}
 * （append-only）与生命周期端口 {@link AuthorizationDecisionAuditLifecycleRepository}
 * （归档、并集分页、状态快照）。两个端口共用同一张表的 mapper，但按能力分开暴露：
 * 请求决策链路只拿到 insert。
 */
@Repository
public class MybatisAuthorizationDecisionAuditRepository
        implements AuthorizationDecisionAuditRepository, AuthorizationDecisionAuditLifecycleRepository {

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

    @Override
    public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) {
        return mapper.archiveBefore(cutoff, archivedAt, batchSize);
    }

    @Override
    public List<AuthorizationDecisionAudit> findPage(
            UUID workspaceId, @Nullable AuthorizationDecisionAuditCursor cursor, int limit) {
        return mapper.selectPage(
                        workspaceId,
                        cursor == null ? null : cursor.evaluatedAt(),
                        cursor == null ? null : cursor.decisionId(),
                        limit)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public AuthorizationDecisionAuditOperationalStatus operationalStatus() {
        AuthorizationDecisionAuditOperationalStatusRow row = mapper.selectOperationalStatus();
        return new AuthorizationDecisionAuditOperationalStatus(
                row.getHot(), row.getArchived(), row.getOldestHotAt());
    }

    private AuthorizationDecisionAudit toDomain(AuthorizationDecisionAuditRow row) {
        return new AuthorizationDecisionAudit(
                row.getDecisionId(),
                row.getWorkspaceId(),
                row.getRequesterIssuer(),
                row.getRequesterType(),
                row.getRequesterId(),
                row.getPermissionCode(),
                row.getResourceType(),
                row.getResourceId(),
                AuthorizationOutcome.valueOf(row.getOutcome()),
                row.getReasonCode(),
                row.getPolicyVersion(),
                row.getRequestId(),
                row.getTraceId(),
                row.getEvaluatedAt());
    }
}
