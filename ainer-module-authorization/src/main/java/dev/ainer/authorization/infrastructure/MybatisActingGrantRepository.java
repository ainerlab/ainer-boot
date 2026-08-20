package dev.ainer.authorization.infrastructure;

import dev.ainer.authorization.application.ActingGrantRepository;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@code ainer_authorization_acting_grant} 的 MyBatis 适配器（ADR-0043 A1）。 */
@Repository
public class MybatisActingGrantRepository implements ActingGrantRepository {

    private final ActingGrantMapper mapper;
    private final Clock clock;

    public MybatisActingGrantRepository(ActingGrantMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UUID save(SubjectRef principal, UUID agentId, String agentVersion,
            Set<PermissionCode> permissions, Scope scope, Instant validFrom, Instant validUntil) {
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
            default -> throw new IllegalArgumentException("ActingGrant must not use GLOBAL scope");
        }
        mapper.insertGrant(id, principal.issuerNamespace(), principal.subjectId(), agentId,
                agentVersion, scopeKind, workspaceId, resourceType, resourceId, validFrom,
                validUntil, now);
        mapper.insertGrantPermissions(id,
                permissions.stream().map(PermissionCode::value).toList());
        return id;
    }

    @Override
    public Optional<PersistedGrant> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Long> revoke(UUID id, Instant revokedAt, String reason) {
        if (mapper.revoke(id, revokedAt, reason, Instant.now(clock)) == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(ActingGrantRow::getVersion);
    }

    @Override
    public List<PersistedGrant> findLiveGrants(SubjectRef principal, Instant at) {
        return mapper.selectLiveByPrincipal(principal.issuerNamespace(),
                principal.subjectId(), at).stream().map(this::toDomain).toList();
    }

    private PersistedGrant toDomain(ActingGrantRow row) {
        Scope scope = switch (row.getScopeKind()) {
            case "WORKSPACE" -> new Scope.Workspace(row.getWorkspaceId());
            case "RESOURCE" -> new Scope.Resource(row.getWorkspaceId(),
                    new ResourceType(row.getResourceType()), row.getResourceId());
            default -> throw new IllegalStateException(
                    "ActingGrant has impossible scope kind: " + row.getScopeKind());
        };
        Set<PermissionCode> permissions = new LinkedHashSet<>();
        for (String code : mapper.selectPermissions(row.getId())) {
            permissions.add(new PermissionCode(code));
        }
        return new PersistedGrant(
                row.getId(),
                new SubjectRef(row.getPrincipalIssuer(), row.getPrincipalSubjectId(),
                        SubjectType.valueOf(row.getPrincipalType())),
                row.getAgentId(),
                row.getAgentVersion(),
                scope,
                BindingStatus.valueOf(row.getStatus()),
                row.getValidFrom(),
                row.getValidUntil(),
                row.getVersion(),
                permissions);
    }
}
