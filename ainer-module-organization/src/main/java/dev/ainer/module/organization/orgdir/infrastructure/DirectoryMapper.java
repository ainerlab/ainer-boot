package dev.ainer.module.organization.orgdir.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DirectoryMapper {

    void insertDirectory(DirectoryRow row);

    void insertUnit(OrgUnitRow row);

    void insertUnitParent(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
            @Param("directoryId") UUID directoryId, @Param("childUnitId") UUID childUnitId,
            @Param("parentUnitId") UUID parentUnitId, @Param("validFrom") Instant validFrom,
            @Param("now") Instant now);

    DirectoryRow selectDirectory(@Param("id") UUID id);

    DirectoryRow selectDirectoryInWorkspace(@Param("workspaceId") UUID workspaceId,
            @Param("id") UUID id);

    List<DirectoryRow> pageDirectories(@Param("workspaceId") UUID workspaceId,
            @Param("offset") long offset, @Param("limit") int limit);

    long countDirectories(@Param("workspaceId") UUID workspaceId);

    OrgUnitRow selectUnit(@Param("directoryId") UUID directoryId, @Param("id") UUID id);

    List<OrgUnitRow> selectUnits(@Param("directoryId") UUID directoryId);

    List<OrgUnitRow> selectUnitAncestors(@Param("directoryId") UUID directoryId,
            @Param("unitId") UUID unitId);
}
