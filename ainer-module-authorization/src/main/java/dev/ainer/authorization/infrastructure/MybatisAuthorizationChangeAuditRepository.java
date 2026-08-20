package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.AuthorizationChangeAudit;
import dev.ainer.authorization.application.AuthorizationChangeAuditRepository;
import org.springframework.stereotype.Repository;

/**
 * {@link AuthorizationChangeAuditRepository} 的 MyBatis 实现（ADR-0030 §11.7）。
 */
@Repository
public class MybatisAuthorizationChangeAuditRepository implements AuthorizationChangeAuditRepository {

    private final AuthorizationChangeAuditMapper mapper;

    public MybatisAuthorizationChangeAuditRepository(AuthorizationChangeAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AuthorizationChangeAudit audit) {
        AuthorizationChangeAuditRow row = new AuthorizationChangeAuditRow();
        row.setActorIssuer(audit.actorIssuer());
        row.setActorType(audit.actorType());
        row.setActorId(audit.actorId());
        row.setTargetType(audit.targetType());
        row.setTargetId(audit.targetId());
        row.setAction(audit.action());
        row.setBeforeVersion(audit.beforeVersion());
        row.setAfterVersion(audit.afterVersion());
        row.setRequestId(audit.requestId());
        row.setTraceId(audit.traceId());
        row.setOccurredAt(audit.occurredAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException(
                    "Authorization change audit insert affected an unexpected number of rows");
        }
    }
}
