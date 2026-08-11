package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Role;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Application use cases for Role management (ADR-0030 S1). Roles created here are persisted and
 * available for binding assignment. Permission codes must be registered in the
 * {@link PermissionRegistry} before they can be assigned to a role — administrators cannot grant
 * permissions that application code does not implement.
 *
 * <p>Management mutations are audited via {@link AuthorizationChangeAuditService} in the same
 * transaction (ADR-0030 §11.7). An audit write failure rolls back the Role change.
 */
@Service
@Transactional
public class RoleApplicationService {

    static final String TARGET_TYPE_ROLE = "ROLE";

    private final RoleRepository roleRepository;
    private final PermissionRegistry permissionRegistry;
    private final AuthorizationChangeAuditService changeAuditService;
    private final Clock clock;

    public RoleApplicationService(
            RoleRepository roleRepository,
            PermissionRegistry permissionRegistry,
            AuthorizationChangeAuditService changeAuditService,
            Clock clock) {
        this.roleRepository = roleRepository;
        this.permissionRegistry = permissionRegistry;
        this.changeAuditService = changeAuditService;
        this.clock = clock;
    }

    /**
     * Create a new persisted role with the given code, display name and permission set.
     *
     * @throws BusinessException if the code is already in use or a permission code is not registered.
     */
    public UUID createRole(
            AuthenticatedPrincipal actor, String code, String name, Set<PermissionCode> permissions,
            @Nullable String requestId, @Nullable String traceId) {
        roleRepository.findActiveByCode(code).ifPresent(existing -> {
            throw new BusinessException(AuthorizationErrorCode.ROLE_ALREADY_EXISTS);
        });
        validatePermissions(permissions);
        Role role = new Role(code, name, permissions);
        UUID roleId = roleRepository.save(role);
        changeAuditService.record(actor, TARGET_TYPE_ROLE, roleId, "CREATE",
                null, 0L, requestId, traceId);
        return roleId;
    }

    /**
     * Atomically replace the permissions of an existing role (optimistic version check).
     *
     * @throws BusinessException if the role is not found, a permission is unregistered, or the version is stale.
     */
    public void replacePermissions(
            AuthenticatedPrincipal actor, UUID roleId, Set<PermissionCode> permissions,
            long expectedVersion, @Nullable String requestId, @Nullable String traceId) {
        RoleRepository.RoleRecord existing = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
        validatePermissions(permissions);
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

    private void validatePermissions(Set<PermissionCode> permissions) {
        Set<PermissionCode> unregistered = new LinkedHashSet<>();
        for (PermissionCode code : permissions) {
            if (permissionRegistry.find(code).isEmpty()) {
                unregistered.add(code);
            }
        }
        if (!unregistered.isEmpty()) {
            throw new BusinessException(AuthorizationErrorCode.PERMISSION_NOT_FOUND);
        }
    }
}
