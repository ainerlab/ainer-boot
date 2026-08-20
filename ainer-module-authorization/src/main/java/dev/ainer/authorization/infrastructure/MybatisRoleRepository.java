package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.RoleRepository;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Role;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link RoleRepository} 的 MyBatis 实现（ADR-0030 S1）。
 */
@Repository
public class MybatisRoleRepository implements RoleRepository {

    private final RoleMapper roleMapper;

    public MybatisRoleRepository(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public UUID save(Role role) {
        RoleRow row = new RoleRow();
        row.setCode(role.code());
        row.setName(role.name());
        row.setSystemRole(false);
        row.setStatus("ACTIVE");
        row.setVersion(0);
        java.time.Instant now = java.time.Instant.now();
        UUID roleId;
        try {
            roleId = roleMapper.insertReturningId(row, now);
        } catch (DuplicateKeyException e) {
            throw new dev.ainer.core.error.BusinessException(
                    dev.ainer.authorization.application.AuthorizationErrorCode.ROLE_ALREADY_EXISTS);
        }
        if (!role.permissions().isEmpty()) {
            List<String> codes = role.permissions().stream().map(PermissionCode::value).toList();
            roleMapper.insertPermissions(roleId, codes, now);
        }
        return roleId;
    }

    @Override
    public Optional<RoleRecord> findById(UUID id) {
        RoleRow row = roleMapper.selectById(id);
        return Optional.ofNullable(row).map(r -> toRecord(r, loadPermissions(r.getId())));
    }

    @Override
    public Optional<RoleRecord> findActiveByCode(String code) {
        RoleRow row = roleMapper.selectActiveByCode(code);
        return Optional.ofNullable(row).map(r -> toRecord(r, loadPermissions(r.getId())));
    }

    @Override
    public Optional<Long> replacePermissions(UUID roleId, Set<PermissionCode> permissions, long expectedVersion) {
        List<String> codes = permissions.stream().map(PermissionCode::value).toList();
        java.time.Instant now = java.time.Instant.now();
        roleMapper.deletePermissions(roleId);
        if (!codes.isEmpty()) {
            roleMapper.insertPermissions(roleId, codes, now);
        }
        int affected = roleMapper.bumpVersion(roleId, expectedVersion, now);
        if (affected == 0) {
            return Optional.empty();
        }
        return Optional.of(expectedVersion + 1);
    }

    @Override
    public Collection<RoleRecord> findAll() {
        List<RoleRow> rows = roleMapper.selectAll();
        List<RoleRecord> records = new ArrayList<>(rows.size());
        for (RoleRow row : rows) {
            records.add(toRecord(row, loadPermissions(row.getId())));
        }
        return records;
    }

    @Override
    public Set<PermissionCode> findPermissionCodesByRoleId(UUID roleId) {
        return loadPermissions(roleId);
    }

    private Set<PermissionCode> loadPermissions(UUID roleId) {
        List<String> codes = roleMapper.selectPermissionCodes(roleId);
        Set<PermissionCode> result = new LinkedHashSet<>(codes.size());
        for (String code : codes) {
            result.add(new PermissionCode(code));
        }
        return result;
    }

    private RoleRecord toRecord(RoleRow row, Set<PermissionCode> permissions) {
        Role role = new Role(row.getCode(), row.getName(), permissions);
        return new RoleRecord(row.getId(), role, row.isSystemRole(), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
