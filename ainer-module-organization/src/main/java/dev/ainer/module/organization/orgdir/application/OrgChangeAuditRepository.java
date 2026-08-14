package dev.ainer.module.organization.orgdir.application;

import dev.ainer.module.organization.orgdir.domain.OrgChangeAudit;

import java.util.List;
import java.util.UUID;

/** 组织变更审计端口（append-only，同事务写入）。 */
public interface OrgChangeAuditRepository {

    void insert(OrgChangeAudit audit);

    List<OrgChangeAudit> findByEntity(String entityType, UUID entityId, int limit);

    long countByEntity(UUID entityId);
}
