package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Permission;

import java.util.Collection;

/**
 * Persistence port for the {@link Permission} catalog projection (ADR-0030 S1). The authority at
 * decision time is the in-memory {@link dev.ainer.authorization.catalog.PermissionRegistry}; this
 * repository syncs the registered definitions to the database management projection.
 */
public interface PermissionCatalogRepository {

    /**
     * Upsert a permission definition into the catalog projection. If the code already exists with a
     * differing definition, the conflict is surfaced for startup fail-closed handling; an identical
     * re-registration is idempotent.
     */
    void upsert(Permission permission, String sourceModule);

    Collection<Permission> findAll();
}
