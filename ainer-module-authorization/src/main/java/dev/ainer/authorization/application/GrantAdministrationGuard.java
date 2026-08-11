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
 * Non-bypassable guard for generic Role and Binding administration.
 *
 * <p>The controller calls this guard for management reads, while application mutation services call
 * it again at the transaction boundary. This keeps direct service invocations from bypassing the
 * anti-escalation rules. Hard invariants (non-GLOBAL, non-system-only and no self modification) are
 * enforced here in addition to the host's {@link GrantAdministrationPolicy}.
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

    /** Require SERVICE + management scope + the exact host-registered trusted manager. */
    public void requireManager(AuthenticatedPrincipal actor) {
        if (!actor.isService()
                || !actor.principalSubjectRef().authority().equals(actor.authority())
                || !actor.hasScope(MANAGE_SCOPE)
                || !policy.isTrustedManager(actor)) {
            throw new BusinessException(AuthorizationErrorCode.GRANT_ADMINISTRATION_DENIED);
        }
    }

    /** Validate a new Role's complete permission set against the assignable catalog. */
    public void requireRoleCreation(
            AuthenticatedPrincipal actor, Set<PermissionCode> permissions) {
        requireManager(actor);
        requireAssignablePermissions(actor, permissions);
    }

    /**
     * Validate a Role permission replacement and reject modification of any Role referenced by an
     * ACTIVE Binding for the actor itself, including future-dated bindings.
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

    /** Validate target, Role permissions and scope before creating a Binding. */
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

    /** Validate a Binding mutation, including the generic API's no-self-modification invariant. */
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
