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
 * Role 管理的应用用例（ADR-0030 S1）。这里创建的 Role 会被持久化，并可用于 Binding
 * 分配。权限 code 必须先存在于已注册目录，才能被赋予 Role——管理员无法授予应用代码
 * 未实现的权限。
 *
 * <p>管理变更通过 {@link AuthorizationChangeAuditService} 在同一事务内审计
 * （ADR-0030 §11.7）。审计写失败会回滚 Role 变更。
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
     * 以给定 code、显示名与权限集合创建新的持久化 Role。
     *
     * @throws BusinessException 当管理者不受信、code 已被占用，或某权限未注册/不可分配时。
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
     * 原子替换既有 Role 的权限集合（乐观版本检查）。
     *
     * @throws BusinessException 当管理者不受信、Role 绑定到操作者自身、Role/权限不合法，
     *                           或版本已过期时。
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
     * 按数据库 id 查找 Role。
     *
     * @throws BusinessException 当 Role 不存在时。
     */
    @Transactional(readOnly = true)
    public RoleRepository.RoleRecord getRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(AuthorizationErrorCode.ROLE_NOT_FOUND));
    }

}
