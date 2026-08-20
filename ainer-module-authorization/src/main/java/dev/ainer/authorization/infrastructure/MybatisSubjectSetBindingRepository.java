package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.SubjectSetBindingRepository;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectSetRef;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code ainer_authorization_subject_set_binding} 的 MyBatis 适配器（ADR-0042 O2）。 */
@Repository
public class MybatisSubjectSetBindingRepository implements SubjectSetBindingRepository {

    private final SubjectSetBindingMapper mapper;
    private final Clock clock;

    public MybatisSubjectSetBindingRepository(SubjectSetBindingMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UUID save(
            SubjectSetRef set, UUID roleId, Scope scope, Instant validFrom, Instant validUntil) {
        UUID id = dev.ainer.core.uuid.Uuidv7.generate();
        Instant now = Instant.now(clock);
        String scopeKind;
        UUID workspaceId = null;
        String resourceType = null;
        UUID resourceId = null;
        switch (scope) {
            case Scope.Workspace ws -> {
                scopeKind = "WORKSPACE";
                workspaceId = ws.workspaceId();
            }
            case Scope.Resource res -> {
                scopeKind = "RESOURCE";
                workspaceId = res.workspaceId();
                resourceType = res.resourceType().value();
                resourceId = res.resourceId();
            }
            case Scope.Global ignored ->
                    throw new IllegalArgumentException("SubjectSetBinding must not use GLOBAL scope");
        }
        mapper.insert(id, set.objectType(), set.objectId(), set.relation(), set.workspaceId(),
                set.directoryId(), roleId, scopeKind, workspaceId, resourceType, resourceId,
                validFrom, validUntil, now);
        return id;
    }

    @Override
    public Optional<PersistedSetBinding> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSubjectSetBindingRepository::toDomain);
    }

    @Override
    public Optional<Long> revoke(UUID id, Instant revokedAt, String reason) {
        Instant now = Instant.now(clock);
        int updated = mapper.revoke(id, revokedAt, reason, now);
        if (updated == 0) {
            return Optional.empty();
        }
        if (mapper.selectById(id) == null) {
            return Optional.empty();
        }
        return Optional.of(mapper.selectById(id).getVersion());
    }

    @Override
    public List<PersistedSetBinding> findLiveSetBindings(
            dev.ainer.authorization.domain.ResourceRef resource, Instant at) {
        SubjectSetBindingMapper.ResourceFilter filter = new SubjectSetBindingMapper.ResourceFilter(
                resource.workspaceId(),
                resource.resourceType() == null ? null : resource.resourceType().value(),
                resource.resourceId());
        return mapper.selectLive(filter, at).stream()
                .map(MybatisSubjectSetBindingRepository::toDomain)
                .toList();
    }

    private static PersistedSetBinding toDomain(SetBindingRow row) {
        SubjectSetRef set = new SubjectSetRef(
                row.getSetObjectType(), row.getSetObjectId(), row.getSetRelation(),
                row.getSetWorkspaceId(), row.getSetDirectoryId());
        Scope scope = switch (row.getScopeKind()) {
            case "WORKSPACE" -> new Scope.Workspace(row.getWorkspaceId());
            case "RESOURCE" -> new Scope.Resource(row.getWorkspaceId(),
                    new ResourceType(row.getResourceType()), row.getResourceId());
            default -> throw new IllegalStateException(
                    "SubjectSetBinding has impossible scope kind: " + row.getScopeKind());
        };
        return new PersistedSetBinding(row.getId(), set, row.getRoleId(), scope,
                BindingStatus.valueOf(row.getStatus()), row.getValidFrom(), row.getValidUntil(),
                row.getVersion());
    }
}
