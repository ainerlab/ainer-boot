package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.GrantAdministrationPolicy;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 通用 Role 与 Binding 管理的不可绕过守卫。
 *
 * <p>控制器在管理读取时调用本守卫，应用层变更服务在事务边界再次调用。这保证直接调用
 * 服务也无法绕过防提权规则。除宿主的 {@link GrantAdministrationPolicy} 之外，硬性不变量
 * （非 GLOBAL、非 system-only、禁止自我修改）在这里统一强制。
 */
@Component
public final class GrantAdministrationGuard {

    public static final String MANAGE_SCOPE = "authorization.manage";

    private final GrantAdministrationPolicy policy;
    private final PermissionRegistry permissionRegistry;
    private final SubjectBindingRepository bindingRepository;

    public GrantAdministrationGuard(
            ObjectProvider<GrantAdministrationPolicy> policyProvider,
            PermissionRegistry permissionRegistry,
            SubjectBindingRepository bindingRepository) {
        this.policy = policyProvider.getIfAvailable(GrantAdministrationGuard::denyAllPolicy);
        this.permissionRegistry = permissionRegistry;
        this.bindingRepository = bindingRepository;
        if (policy.version() == null || policy.version().isBlank()) {
            throw new IllegalStateException("GrantAdministrationPolicy.version must be non-blank");
        }
    }

    /** 要求 SERVICE 主体 + 管理 scope + 宿主注册的精确受信管理者。 */
    public void requireManager(AuthenticatedPrincipal actor) {
        if (!actor.isService()
                || !actor.principalSubjectRef().authority().equals(actor.authority())
                || !actor.hasScope(MANAGE_SCOPE)
                || !policy.isTrustedManager(actor)) {
            throw new BusinessException(AuthorizationErrorCode.GRANT_ADMINISTRATION_DENIED);
        }
    }

    /** 按可分配目录校验新 Role 的完整权限集合。 */
    public void requireRoleCreation(
            AuthenticatedPrincipal actor, Set<PermissionCode> permissions) {
        requireManager(actor);
        requireAssignablePermissions(actor, permissions);
    }

    /**
     * 校验 Role 权限替换，并拒绝修改任何被操作者自身 ACTIVE Binding（包括未来生效的
     * Binding）引用的 Role。
     */
    public void requireRoleModification(
            AuthenticatedPrincipal actor, UUID roleId, Set<PermissionCode> permissions) {
        requireManager(actor);
        SubjectRef actorRef = actorSubject(actor);
        boolean boundToActor = bindingRepository.findAllBySubject(actorRef).stream()
                .anyMatch(binding -> binding.status() == BindingStatus.ACTIVE
                        && binding.roleId().equals(roleId));
        if (boundToActor) {
            throw new BusinessException(AuthorizationErrorCode.SELF_GRANT_FORBIDDEN);
        }
        requireAssignablePermissions(actor, permissions);
    }

    /** 创建 Binding 前校验目标、Role 权限与 scope。 */
    public void requireBindingCreation(
            AuthenticatedPrincipal actor,
            SubjectRef target,
            RoleRepository.RoleRecord role,
            Scope scope) {
        requireManager(actor);
        if (actorSubject(actor).equals(target)) {
            throw new BusinessException(AuthorizationErrorCode.SELF_GRANT_FORBIDDEN);
        }
        if (!policy.isTargetAssignable(actor, target)) {
            throw new BusinessException(AuthorizationErrorCode.GRANT_ADMINISTRATION_DENIED);
        }
        if (scope instanceof Scope.Global || !policy.isScopeAssignable(actor, scope)) {
            throw new BusinessException(AuthorizationErrorCode.SCOPE_NOT_ASSIGNABLE);
        }
        requireAssignablePermissions(actor, role.role().permissions());
    }

    /**
     * 校验主体集合 Binding 的创建（ADR-0042 O2，承接 ADR-0032 §6 防提权）：
     * GLOBAL 不可授予；set 与 scope 必须同 Workspace；set 家族必须有已注册成员解析器；
     * 管理者当前不得是目标集合成员（自提权防护）；system-only 与 HIGH 风险权限不得
     * 通过集合授予；其余约束与直接 Binding 相同。
     */
    public void requireSetBindingCreation(
            AuthenticatedPrincipal actor,
            dev.ainer.authorization.domain.SubjectSetRef set,
            RoleRepository.RoleRecord role,
            dev.ainer.authorization.domain.Scope scope,
            dev.ainer.authorization.policy.SubjectSetMembershipRegistry membershipRegistry) {
        requireManager(actor);
        if (scope instanceof dev.ainer.authorization.domain.Scope.Global
                || !policy.isScopeAssignable(actor, scope)) {
            throw new BusinessException(AuthorizationErrorCode.SCOPE_NOT_ASSIGNABLE);
        }
        UUID scopeWorkspaceId = switch (scope) {
            case dev.ainer.authorization.domain.Scope.Workspace ws -> ws.workspaceId();
            case dev.ainer.authorization.domain.Scope.Resource res -> res.workspaceId();
            default -> null;
        };
        if (scopeWorkspaceId == null || !scopeWorkspaceId.equals(set.workspaceId())) {
            throw new BusinessException(AuthorizationErrorCode.SUBJECT_SET_SCOPE_MISMATCH);
        }
        if (!membershipRegistry.supports(set)) {
            throw new BusinessException(AuthorizationErrorCode.UNKNOWN_SUBJECT_SET);
        }
        dev.ainer.authorization.policy.SubjectSetMembership selfMembership =
                membershipRegistry.membership(actorSubject(actor), set, java.time.Instant.now());
        if (selfMembership.isMember()) {
            throw new BusinessException(AuthorizationErrorCode.SELF_GRANT_FORBIDDEN);
        }
        if (selfMembership.status()
                == dev.ainer.authorization.policy.SubjectSetMembership.Status.UNAVAILABLE) {
            // 自提权防线失败关闭：成员事实不可读时拒绝创建，不得因解析失败放行。
            throw new BusinessException(AuthorizationErrorCode.UNKNOWN_SUBJECT_SET);
        }
        for (dev.ainer.authorization.domain.PermissionCode code : role.role().permissions()) {
            Permission permission = permissionRegistry.find(code)
                    .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_FOUND));
            if (permission.systemOnly() || permission.riskTier() == dev.ainer.authorization.domain.RiskTier.HIGH) {
                throw new BusinessException(AuthorizationErrorCode.SUBJECT_SET_PERMISSION_FORBIDDEN);
            }
            if (!policy.isPermissionAssignable(actor, permission)) {
                throw new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_ASSIGNABLE);
            }
        }
    }

    /** 校验 Binding 变更，包含通用 API 的禁止自我修改不变量。 */
    public void requireBindingRevocation(
            AuthenticatedPrincipal actor, SubjectBindingRepository.PersistedBinding binding) {
        requireManager(actor);
        if (actorSubject(actor).equals(binding.subjectRef())) {
            throw new BusinessException(AuthorizationErrorCode.SELF_GRANT_FORBIDDEN);
        }
    }

    private void requireAssignablePermissions(
            AuthenticatedPrincipal actor, Set<PermissionCode> permissionCodes) {
        for (PermissionCode code : permissionCodes) {
            Permission permission = permissionRegistry.find(code)
                    .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_FOUND));
            if (permission.systemOnly() || !policy.isPermissionAssignable(actor, permission)) {
                throw new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_ASSIGNABLE);
            }
        }
    }

    private static SubjectRef actorSubject(AuthenticatedPrincipal actor) {
        SubjectType type = actor.isService() ? SubjectType.SERVICE : SubjectType.USER;
        return new SubjectRef(actor.principalSubjectRef().authority().issuer(), actor.subjectId(), type);
    }

    private static GrantAdministrationPolicy denyAllPolicy() {
        return new GrantAdministrationPolicy() {
            @Override
            public String version() {
                return "ainer-authorization-administration-deny-all-v1";
            }

            @Override
            public boolean isTrustedManager(AuthenticatedPrincipal actor) {
                return false;
            }

            @Override
            public boolean isPermissionAssignable(AuthenticatedPrincipal actor, Permission permission) {
                return false;
            }

            @Override
            public boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope) {
                return false;
            }

            @Override
            public boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target) {
                return false;
            }
        };
    }
}
