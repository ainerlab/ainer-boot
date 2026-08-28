package dev.ainer.module.workspace.workspace.authorization;

import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.AuthorizationPolicyContributor;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorities;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Workspace 对通用授权模块的可组合粗粒度策略贡献。
 *
 * <p>HTTP 拦截器只把同名 scope 作为能力闸门；ACTIVE membership、OWNER/ADMIN 角色和对象归属
 * 继续由 {@code WorkspaceApplicationService} 失败关闭。特别是创建 Workspace 时对象尚不存在，
 * 因此这里不能预先要求 Workspace Binding。
 */
@Configuration(proxyBeanMethods = false)
public class WorkspaceAuthorizationPolicyConfiguration {

    private static final ResourceType REQUEST_RESOURCE = new ResourceType("request");
    private static final PermissionCode READ = new PermissionCode(WorkspaceAuthorities.READ);
    private static final PermissionCode WRITE = new PermissionCode(WorkspaceAuthorities.WRITE);
    private static final PermissionCode AUDIT_READ = new PermissionCode(WorkspaceAuthorities.AUDIT_READ);
    private static final Set<PermissionCode> PERMISSIONS = Set.of(READ, WRITE, AUDIT_READ);

    @Bean
    AuthorizationPolicyContributor workspaceAuthorizationPolicyContributor() {
        return new AuthorizationPolicyContributor() {
            @Override
            public Set<Permission> permissions() {
                return Set.of(
                        permission(READ, "read", RiskTier.LOW),
                        permission(WRITE, "write", RiskTier.MEDIUM),
                        permission(AUDIT_READ, "read", RiskTier.LOW));
            }

            @Override
            public boolean scopePermits(String scope, PermissionCode permission) {
                return scope.equals(permission.value()) && PERMISSIONS.contains(permission);
            }

            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return PERMISSIONS.contains(permission) ? GrantPath.RELATION_DERIVED : null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return PERMISSIONS.contains(permission);
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                return PERMISSIONS.contains(permission)
                        && REQUEST_RESOURCE.equals(resource.resourceType());
            }
        };
    }

    private static Permission permission(
            PermissionCode code, String action, RiskTier riskTier) {
        return new Permission(
                code, action, REQUEST_RESOURCE, riskTier,
                AuditLevel.ON_DECISION, false, true);
    }
}
