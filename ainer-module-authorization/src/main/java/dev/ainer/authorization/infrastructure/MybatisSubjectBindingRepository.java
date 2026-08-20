package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.SubjectBindingRepository;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link SubjectBindingRepository} 的 MyBatis 实现（ADR-0030 S1）。
 */
@Repository
public class MybatisSubjectBindingRepository implements SubjectBindingRepository {

    private final SubjectBindingMapper bindingMapper;
    private final RoleMapper roleMapper;

    public MybatisSubjectBindingRepository(SubjectBindingMapper bindingMapper, RoleMapper roleMapper) {
        this.bindingMapper = bindingMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UUID save(SubjectRef subject, UUID roleId, Scope scope, Instant validFrom, Instant validUntil) {
        SubjectBindingRow row = toRow(subject, roleId, scope, validFrom, validUntil);
        return bindingMapper.insertReturningId(row, Instant.now());
    }

    @Override
    public Optional<PersistedBinding> findById(UUID id) {
        SubjectBindingRow row = bindingMapper.selectById(id);
        return Optional.ofNullable(row).map(r -> toPersisted(r, loadRoleCode(r.getRoleId())));
    }

    @Override
    public Optional<Long> revoke(UUID id, Instant revokedAt, String reason) {
        int affected = bindingMapper.revoke(id, revokedAt, reason, Instant.now());
        if (affected == 0) {
            return Optional.empty();
        }
        return Optional.of(-1L);
    }

    @Override
    public List<PersistedBinding> findLiveBindings(SubjectRef subject, Instant at) {
        List<SubjectBindingRow> rows = bindingMapper.selectLiveBindings(
                subject.issuerNamespace(), subject.type().name(), subject.subjectId(), at);
        return rows.stream().map(r -> toPersisted(r, loadRoleCode(r.getRoleId()))).toList();
    }

    @Override
    public List<PersistedBinding> findAllBySubject(SubjectRef subject) {
        List<SubjectBindingRow> rows = bindingMapper.selectAllBySubject(
                subject.issuerNamespace(), subject.type().name(), subject.subjectId());
        return rows.stream().map(r -> toPersisted(r, loadRoleCode(r.getRoleId()))).toList();
    }

    private String loadRoleCode(UUID roleId) {
        RoleRow role = roleMapper.selectById(roleId);
        return role != null ? role.getCode() : null;
    }

    private SubjectBindingRow toRow(SubjectRef subject, UUID roleId, Scope scope,
                                     Instant validFrom, Instant validUntil) {
        SubjectBindingRow row = new SubjectBindingRow();
        row.setIssuer(subject.issuerNamespace());
        row.setSubjectType(subject.type().name());
        row.setSubjectId(subject.subjectId());
        row.setRoleId(roleId);
        applyScope(row, scope);
        row.setValidFrom(validFrom);
        row.setValidUntil(validUntil);
        row.setStatus("ACTIVE");
        row.setVersion(0);
        return row;
    }

    private void applyScope(SubjectBindingRow row, Scope scope) {
        switch (scope) {
            case Scope.Global ignored -> {
                row.setScopeKind("GLOBAL");
                row.setWorkspaceId(null);
                row.setResourceType(null);
                row.setResourceId(null);
            }
            case Scope.Workspace ws -> {
                row.setScopeKind("WORKSPACE");
                row.setWorkspaceId(ws.workspaceId());
                row.setResourceType(null);
                row.setResourceId(null);
            }
            case Scope.Resource res -> {
                row.setScopeKind("RESOURCE");
                row.setWorkspaceId(res.workspaceId());
                row.setResourceType(res.resourceType().value());
                row.setResourceId(res.resourceId());
            }
        }
    }

    private PersistedBinding toPersisted(SubjectBindingRow row, String roleCode) {
        Scope scope = extractScope(row);
        SubjectRef subjectRef = new SubjectRef(
                row.getIssuer(), row.getSubjectId(), SubjectType.valueOf(row.getSubjectType()));
        BindingStatus status = BindingStatus.valueOf(row.getStatus());
        return new PersistedBinding(
                row.getId(),
                subjectRef,
                row.getRoleId(),
                roleCode,
                scope,
                status,
                row.getValidFrom(),
                row.getValidUntil(),
                row.getVersion(),
                row.getRevokedAt(),
                row.getRevokedReason());
    }

    private Scope extractScope(SubjectBindingRow row) {
        return switch (row.getScopeKind()) {
            case "GLOBAL" -> new Scope.Global();
            case "WORKSPACE" -> new Scope.Workspace(row.getWorkspaceId());
            case "RESOURCE" -> new Scope.Resource(
                    row.getWorkspaceId(),
                    new dev.ainer.authorization.domain.ResourceType(row.getResourceType()),
                    row.getResourceId());
            default -> throw new IllegalStateException("Unknown scope_kind: " + row.getScopeKind());
        };
    }
}
