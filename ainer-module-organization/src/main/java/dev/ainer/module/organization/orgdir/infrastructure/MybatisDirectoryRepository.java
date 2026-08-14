package dev.ainer.module.organization.orgdir.infrastructure;

import dev.ainer.module.organization.orgdir.application.DirectoryRepository;
import dev.ainer.module.organization.orgdir.domain.OrgDirectory;
import dev.ainer.module.organization.orgdir.domain.OrgUnit;
import dev.ainer.module.organization.orgdir.domain.OrgUnitParent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisDirectoryRepository implements DirectoryRepository {

    private final DirectoryMapper mapper;

    public MybatisDirectoryRepository(DirectoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertDirectory(OrgDirectory directory) {
        DirectoryRow row = new DirectoryRow();
        row.setId(directory.id());
        row.setWorkspaceId(directory.workspaceId());
        row.setCode(directory.code());
        row.setDisplayName(directory.displayName());
        row.setStatus(directory.status().name());
        row.setVersion(directory.version());
        row.setCreatedAt(directory.createdAt());
        row.setUpdatedAt(directory.updatedAt());
        mapper.insertDirectory(row);
    }

    @Override
    public void insertUnit(OrgUnit unit) {
        mapper.insertUnit(toUnitRow(unit));
    }

    @Override
    public void insertUnitParent(OrgUnitParent parent) {
        mapper.insertUnitParent(parent.id(), parent.workspaceId(), parent.directoryId(),
                parent.childUnitId(), parent.parentUnitId(), parent.validFrom(),
                parent.createdAt());
    }

    @Override
    public Optional<OrgDirectory> findDirectory(UUID id) {
        return Optional.ofNullable(mapper.selectDirectory(id)).map(MybatisDirectoryRepository::toDirectory);
    }

    @Override
    public Optional<OrgDirectory> findDirectory(UUID workspaceId, UUID id) {
        return Optional.ofNullable(mapper.selectDirectoryInWorkspace(workspaceId, id))
                .map(MybatisDirectoryRepository::toDirectory);
    }

    @Override
    public List<OrgDirectory> pageDirectories(UUID workspaceId, long offset, int limit) {
        return mapper.pageDirectories(workspaceId, offset, limit)
                .stream().map(MybatisDirectoryRepository::toDirectory).toList();
    }

    @Override
    public long countDirectories(UUID workspaceId) {
        return mapper.countDirectories(workspaceId);
    }

    @Override
    public Optional<OrgUnit> findUnit(UUID directoryId, UUID unitId) {
        return Optional.ofNullable(mapper.selectUnit(directoryId, unitId))
                .map(MybatisDirectoryRepository::toUnit);
    }

    @Override
    public List<OrgUnit> findUnits(UUID directoryId) {
        return mapper.selectUnits(directoryId).stream()
                .map(MybatisDirectoryRepository::toUnit).toList();
    }

    @Override
    public List<OrgUnit> findUnitAncestors(UUID directoryId, UUID unitId) {
        return mapper.selectUnitAncestors(directoryId, unitId).stream()
                .map(MybatisDirectoryRepository::toUnit).toList();
    }

    private static OrgDirectory toDirectory(DirectoryRow row) {
        return new OrgDirectory(row.getId(), row.getWorkspaceId(), row.getCode(),
                row.getDisplayName(), dev.ainer.module.organization.orgdir.domain.OrgStatus
                        .valueOf(row.getStatus()), row.getVersion(), row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static OrgUnit toUnit(OrgUnitRow row) {
        return new OrgUnit(row.getId(), row.getWorkspaceId(), row.getDirectoryId(), row.getCode(),
                row.getDisplayName(),
                dev.ainer.module.organization.orgdir.domain.OrgUnitKind.valueOf(row.getKind()),
                dev.ainer.module.organization.orgdir.domain.OrgStatus.valueOf(row.getStatus()),
                row.getVersion(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private static OrgUnitRow toUnitRow(OrgUnit unit) {
        OrgUnitRow row = new OrgUnitRow();
        row.setId(unit.id());
        row.setWorkspaceId(unit.workspaceId());
        row.setDirectoryId(unit.directoryId());
        row.setCode(unit.code());
        row.setDisplayName(unit.displayName());
        row.setKind(unit.kind().name());
        row.setStatus(unit.status().name());
        row.setVersion(unit.version());
        row.setCreatedAt(unit.createdAt());
        row.setUpdatedAt(unit.updatedAt());
        return row;
    }
}
