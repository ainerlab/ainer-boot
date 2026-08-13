package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Role;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Application use cases for Role management (ADR-0030 S1). Roles created here are persisted and
 * available for binding assignment. Permission codes must be present in the registered catalog
 * before they can be assigned to a role — administrators cannot grant permissions that application
 * code does not implement.
 *
 * <p>Management mutations are audited via {@link AuthorizationChangeAuditService} in the same
 * transaction (ADR-0030 §11.7). An audit write failure rolls back the Role change.
 */
@Service
@Transactional
public class RoleApplicationService {

    static final String TARGET_TYPE_ROLE = "ROLE";

    private final RoleRepository roleRepository;
    private final GrantAdministrationGuard administrationGuard;
    private final AuthorizationChangeAuditService changeAuditService;

    public RoleApplicationService(
            RoleRepository roleRepository,
            GrantAdministrationGuard administrationGuard,
            AuthorizationChangeAuditService changeAuditService) {
        this.roleRepository = roleRepository;
        this.administrationGuard = administrationGuard;
        this.changeAuditService = changeAuditService;
    }

    /**
     * Create a new persisted role with the given code, display name and permission set.
     *
     * @throws BusinessException if the manager is not trusted, the code is already in use, or a
     *                           permission is unregistered/non-assignable.
     */
    public UUID createRole(
            AuthenticatedPrincipal actor, String code, String name, Set<PermissionCode> permissions,
            @Nullable String requestId, @Nullable String traceId) {
        administrationGuard.requireRoleCreation(actor, permissions);
        roleRepository.findActiveByCode(code).ifPresent(existing -> {
            throw new BusinessException(AuthorizationErrorCode.ROLE_ALREADY_EXISTS);
        });
        Role role = new Role(code, name, permissions);
        UUID roleId = roleRepository.save(role);
        changeAuditService.record(actor, TARGET_TYPE_ROLE, roleId, "CREATE",
                null, 0L, requestId, traceId);
        return roleId;
    }

    /**
     * Atomically replace the permissions of an existing role (optimistic version check).
     *
     * @throws BusinessException if the manager is not trusted, the role is bound to the actor, the
     *                           role/permission is invalid, or the version is stale.
     */
    public void replacePermissions(
            AuthenticatedPrincipal actor, UUID roleId, Set<PermissionCode> permissions,
            long expectedVersion, @Nullable String requestId, @Nullable String traceId) {
        administrationGuard.requireManager(actor);
        RoleRepository.RoleRecord existing = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
        administrationGuard.requireRoleModification(actor, existing.id(), permissions);
        roleRepository.replacePermissions(roleId, permissions, expectedVersion)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.CONCURRENT_MODIFICATION));
        changeAuditService.record(actor, TARGET_TYPE_ROLE, roleId, "REPLACE_PERMISSIONS",
                expectedVersion, expectedVersion + 1, requestId, traceId);
    }

    /**
     * Look up a role by its database id.
     *
     * @throws BusinessException if the role is not found.
     */
    @Transactional(readOnly = true)
    public RoleRepository.RoleRecord getRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
    }

}
