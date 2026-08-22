package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.ActingGrant;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.AgentDefinitionStatusResolver;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ActingGrant 用例与委托检查点（ADR-0043 A1）。签发时强制"不扩权子集"约束（权限已注册
 * 且可委托给 agent、且在 principal 的 live 有效权限内；scope 被某个 live Binding 覆盖；
 * GLOBAL 不可表达）。检查点在每次调用时重新解析授权、principal 的 live Binding 与 agent
 * 状态——principal 权限收缩、Binding 撤销、授权撤销或 agent 退役都会让下一次检查点
 * 立即拒绝（pull 式，无缓存）。
 */
@Service
@Transactional
public class ActingGrantApplicationService {

    static final String TARGET_TYPE_ACTING_GRANT = "ACTING_GRANT";

    private final ActingGrantRepository grantRepository;
    private final PermissionRegistry permissionRegistry;
    private final BindingResolver bindingResolver;
    private final AgentDefinitionStatusResolver agentStatusResolver;
    private final GrantAdministrationGuard administrationGuard;
    private final AuthorizationChangeAuditService changeAuditService;
    private final Clock clock;

    public ActingGrantApplicationService(
            ActingGrantRepository grantRepository,
            PermissionRegistry permissionRegistry,
            BindingResolver bindingResolver,
            AgentDefinitionStatusResolver agentStatusResolver,
            GrantAdministrationGuard administrationGuard,
            AuthorizationChangeAuditService changeAuditService,
            Clock clock) {
        this.grantRepository = grantRepository;
        this.permissionRegistry = permissionRegistry;
        this.bindingResolver = bindingResolver;
        this.agentStatusResolver = agentStatusResolver;
        this.administrationGuard = administrationGuard;
        this.changeAuditService = changeAuditService;
        this.clock = clock;
    }

    /**
     * 签发单层授权。任何子集约束被违反时一律 fail-closed。
     */
    public UUID issueGrant(
            AuthenticatedPrincipal actor, SubjectRef principal, UUID agentId, String agentVersion,
            Set<PermissionCode> permissions, Scope scope, Instant validFrom,
            @Nullable Instant validUntil, @Nullable String requestId) {
        administrationGuard.requireManager(actor);
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agentVersion, "agentVersion");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(validFrom, "validFrom");
        if (principal.type() != SubjectType.USER) {
            throw new BusinessException(AuthorizationErrorCode.SUBJECT_SET_PERMISSION_FORBIDDEN);
        }
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
        }
        if (agentStatusResolver.agentStatus(agentId)
                != AgentDefinitionStatusResolver.AgentStatus.ACTIVE) {
            throw new BusinessException(AuthorizationErrorCode.UNKNOWN_SUBJECT_SET);
        }
        ResourceRef anchor = anchorOf(scope);
        for (PermissionCode code : permissions) {
            Permission permission = permissionRegistry.find(code)
                    .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_FOUND));
            if (permission.systemOnly() || !permission.agentDelegable()) {
                throw new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_ASSIGNABLE);
            }
            if (!principalHasLiveGrant(principal, code, anchor, clock.instant())) {
                throw new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_ASSIGNABLE);
            }
        }
        UUID grantId = grantRepository.save(principal, agentId, agentVersion, permissions, scope,
                validFrom, validUntil);
        changeAuditService.record(actor, TARGET_TYPE_ACTING_GRANT, grantId, "CREATE",
                null, 0L, requestId, null);
        return grantId;
    }

    /** 签发立即生效的单层授权：生效时间由服务端时钟决定。 */
    public UUID issueGrant(
            AuthenticatedPrincipal actor, SubjectRef principal, UUID agentId, String agentVersion,
            Set<PermissionCode> permissions, Scope scope,
            @Nullable Instant validUntil, @Nullable String requestId) {
        return issueGrant(actor, principal, agentId, agentVersion, permissions, scope,
                Instant.now(clock), validUntil, requestId);
    }

    public void revokeGrant(
            AuthenticatedPrincipal actor, UUID grantId, @Nullable String reason,
            @Nullable String requestId) {
        administrationGuard.requireManager(actor);
        grantRepository.findById(grantId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ACTING_GRANT_NOT_FOUND));
        grantRepository.revoke(grantId, Instant.now(clock), reason)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ACTING_GRANT_ALREADY_REVOKED));
        changeAuditService.record(actor, TARGET_TYPE_ACTING_GRANT, grantId, "REVOKE",
                null, null, requestId, null);
    }

    @Transactional(readOnly = true)
    public Optional<ActingGrantRepository.PersistedGrant> findById(UUID id) {
        return grantRepository.findById(id);
    }

    /**
     * 委托检查点：{@code principal} 由 {@code agentId} 执行的、对 {@code resource} 的
     * {@code permission} 操作能否通过某条 live 授权？pull 式：每次调用都重新解析授权
     * 有效性、principal 的 live Binding（防扩权）与 agent 状态。
     */
    @Transactional(readOnly = true)
    public DelegationCheck check(
            SubjectRef principal, UUID agentId, PermissionCode permission, ResourceRef resource,
            String policyVersion) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resource, "resource");
        Instant at = Instant.now(clock);
        List<ActingGrantRepository.PersistedGrant> grants =
                grantRepository.findLiveGrants(principal, at);
        for (ActingGrantRepository.PersistedGrant grant : grants) {
            if (!grant.agentId().equals(agentId) || grant.status() != dev.ainer.authorization.domain.BindingStatus.ACTIVE) {
                continue;
            }
            ActingGrant domainGrant = new ActingGrant(grant.id(), grant.principal(), grant.agentId(),
                    grant.agentVersion(), grant.scope(), grant.status(), grant.validFrom(),
                    grant.validUntil(), grant.version(), grant.permissions());
            if (!domainGrant.isLive(permission, resource, at)) {
                continue;
            }
            if (agentStatusResolver.agentStatus(agentId)
                    != AgentDefinitionStatusResolver.AgentStatus.ACTIVE) {
                return DelegationCheck.denied(grant.id(), "AGENT_RETIRED");
            }
            if (!principalHasLiveGrant(principal, permission, resource, at)) {
                return DelegationCheck.denied(grant.id(), "PRINCIPAL_SHRUNK");
            }
            return DelegationCheck.allowed(grant.id());
        }
        return DelegationCheck.denied(null, "NO_LIVE_GRANT");
    }

    private boolean principalHasLiveGrant(
            SubjectRef principal, PermissionCode permission, ResourceRef resource, Instant at) {
        return bindingResolver.liveBindings(principal).stream()
                .filter(b -> !(b.scope() instanceof Scope.Global)
                        || principal.type() == SubjectType.SERVICE)
                .anyMatch(b -> b.isLive(permission, resource, at));
    }

    /** 签发子集校验用的锚资源：Workspace scope 只需 workspaceId 匹配（合成 resourceType）。 */
    private static ResourceRef anchorOf(Scope scope) {
        return switch (scope) {
            case Scope.Workspace ws -> new ResourceRef(ws.workspaceId(),
                    new dev.ainer.authorization.domain.ResourceType("workspace.anchor"), ws.workspaceId());
            case Scope.Resource res -> new ResourceRef(res.workspaceId(), res.resourceType(),
                    res.resourceId());
            default -> throw new BusinessException(AuthorizationErrorCode.INVALID_SCOPE);
        };
    }

    /** 检查点结果，携带授权 ID 用于审计关联。 */
    public record DelegationCheck(boolean allowed, @Nullable UUID grantId, String reason) {

        static DelegationCheck allowed(UUID grantId) {
            return new DelegationCheck(true, grantId, "DELEGATION_AUTHORIZED");
        }

        static DelegationCheck denied(@Nullable UUID grantId, String reason) {
            return new DelegationCheck(false, grantId, reason);
        }
    }
}
