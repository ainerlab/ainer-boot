package dev.ainer.module.organization.orgdir.application;

import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.OrgUnitParent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 目录与组织单元持久化端口。 */
public interface DirectoryRepository {

    void insertDirectory(OrgDirectory directory);

    void insertUnit(OrgUnit unit);

    void insertUnitParent(OrgUnitParent parent);

    Optional<OrgDirectory> findDirectory(UUID id);

    Optional<OrgDirectory> findDirectory(UUID workspaceId, UUID id);

    List<OrgDirectory> pageDirectories(UUID workspaceId, long offset, int limit);

    long countDirectories(UUID workspaceId);

    Optional<OrgUnit> findUnit(UUID directoryId, UUID unitId);

    List<OrgUnit> findUnits(UUID directoryId);

    List<OrgUnit> findUnitAncestors(UUID directoryId, UUID unitId);
}
