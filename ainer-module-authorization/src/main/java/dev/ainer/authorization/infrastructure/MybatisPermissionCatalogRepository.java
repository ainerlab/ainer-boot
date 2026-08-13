package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.PermissionCatalogRepository;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.RiskTier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;

/**
 * MyBatis-backed implementation of {@link PermissionCatalogRepository} (ADR-0030 S1).
 * The catalog is a management projection; the in-memory PermissionRegistry remains the authority
 * at decision time.
 */
@Repository
public class MybatisPermissionCatalogRepository implements PermissionCatalogRepository {

    private final PermissionMapper permissionMapper;

    public MybatisPermissionCatalogRepository(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    public void upsert(Permission permission, String sourceModule) {
        PermissionRow row = toRow(permission, sourceModule);
        permissionMapper.upsert(row, java.time.Instant.now());
    }

    @Override
    public Collection<Permission> findAll() {
        java.util.List<PermissionRow> rows = permissionMapper.selectAll();
        java.util.List<Permission> permissions = new ArrayList<>(rows.size());
        for (PermissionRow row : rows) {
            permissions.add(toDomain(row));
        }
        return permissions;
    }

    private PermissionRow toRow(Permission permission, String sourceModule) {
        PermissionRow row = new PermissionRow();
        row.setCode(permission.code().value());
        row.setAction(permission.action());
        row.setResourceType(permission.resourceType().value());
        row.setRiskTier(permission.riskTier().name());
        row.setAuditLevel(permission.auditLevel().name());
        row.setSystemOnly(permission.systemOnly());
        row.setAgentDelegable(permission.agentDelegable());
        row.setSourceModule(sourceModule);
        row.setDefinitionVersion(1);
        return row;
    }

    private Permission toDomain(PermissionRow row) {
        return new Permission(
                new dev.ainer.authorization.domain.PermissionCode(row.getCode()),
                row.getAction(),
                new dev.ainer.authorization.domain.ResourceType(row.getResourceType()),
                RiskTier.valueOf(row.getRiskTier()),
                AuditLevel.valueOf(row.getAuditLevel()),
                row.isSystemOnly(),
                row.isAgentDelegable());
    }
}
