package dev.ainer.module.organization.orgdir.infrastructure;

import dev.ainer.module.organization.orgdir.application.OrgChangeAuditRepository;
import dev.ainer.module.organization.orgdir.domain.OrgChangeAudit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MybatisOrgChangeAuditRepository implements OrgChangeAuditRepository {

    private final OrgAuditMapper mapper;

    public MybatisOrgChangeAuditRepository(OrgAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(OrgChangeAudit audit) {
        AuditRow row = new AuditRow();
        row.setId(audit.id());
        row.setEntityType(audit.entityType());
        row.setEntityId(audit.entityId());
        row.setOperation(audit.operation());
        row.setActorIssuer(audit.actorIssuer());
        row.setActorType(audit.actorType());
        row.setActorId(audit.actorId());
        row.setRequestId(audit.requestId());
        row.setOccurredAt(audit.occurredAt());
        mapper.insert(row);
    }

    @Override
    public List<OrgChangeAudit> findByEntity(String entityType, UUID entityId, int limit) {
        return mapper.selectByEntity(entityType, entityId, limit).stream()
                .map(row -> new OrgChangeAudit(row.getId(), row.getEntityType(),
                        row.getEntityId(), row.getOperation(), row.getActorIssuer(),
                        row.getActorType(), row.getActorId(), row.getRequestId(),
                        row.getOccurredAt()))
                .toList();
    }

    @Override
    public long countByEntity(UUID entityId) {
        return mapper.countByEntity(entityId);
    }
}
