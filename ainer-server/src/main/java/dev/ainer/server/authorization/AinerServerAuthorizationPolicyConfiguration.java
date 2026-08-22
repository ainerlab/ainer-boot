package dev.ainer.server.authorization;

import dev.ainer.authorization.application.PermissionCatalogRepository;
import dev.ainer.authorization.catalog.PermissionContributor;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.GrantAdministrationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 参考装配的授权引擎生产策略（ADR-0037 §9 单体装配）。
 *
 * <p>授权模块的默认策略全部 deny-all（引擎 fail-closed）；没有本配置时，管理 API 与
 * 决策引擎在 ainer-server 中是一条全拒绝的死链。本配置把已装配模块的 scope 注册为
 * 平台权限目录（端点粗门禁用 {@code resourceType=request}，与
 * {@code AinerRequestAuthorizationManager} 合成的资源形状一致），建立 scope→permission
 * 的恒等天花板与 BINDING_REQUIRED 领域策略，并同步目录投影供管理 API 创建 Role/Binding。
 * 产品部署应以自己的领域策略覆盖或取代本参考实现。
 *
 * <p>管理面 fail-closed：{@code ainer.authorization.trusted-managers}（逗号分隔的
 * {@code <issuer>|<sub>} 复合键白名单）为空时，一切授权管理操作拒绝——与组织模块
 * trusted-issuer 的缺省语义一致。
 */
@Configuration(proxyBeanMethods = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "ainer.authorization", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerServerAuthorizationPolicyConfiguration {

    /** 端点粗门禁的合成资源类型（与 AinerRequestAuthorizationManager#resolveResource 一致）。 */
    static final ResourceType REQUEST_RESOURCE = new ResourceType("request");

    /** 平台 scope → 平台权限（恒等映射）：写入风险面标 MEDIUM，读取标 LOW。 */
    private static final Set<PlatformPermission> PLATFORM_PERMISSIONS = Set.of(
            new PlatformPermission("workspace.read", false),
            new PlatformPermission("workspace.write", true),
            new PlatformPermission("workspace.audit.read", false),
            new PlatformPermission("file.read", false),
            new PlatformPermission("file.write", true),
            new PlatformPermission("dictionary.read", false),
            new PlatformPermission("dictionary.manage", true),
            new PlatformPermission("config.read", false),
            new PlatformPermission("config.manage", true),
            new PlatformPermission("notification.read", false),
            new PlatformPermission("notification.manage", true),
            new PlatformPermission("notification.submit", true),
            new PlatformPermission("organization.read", false),
            new PlatformPermission("organization.manage", true),
            new PlatformPermission("knowledge.read", false),
            new PlatformPermission("knowledge.manage", true),
            new PlatformPermission("task.read", false),
            new PlatformPermission("task.submit", true),
            new PlatformPermission("task.manage", true),
            new PlatformPermission("ai.invoke", true),
            new PlatformPermission("ai.agents.manage", true));

    private record PlatformPermission(String code, boolean mutating) {
    }

    @Bean
    PermissionContributor ainerServerPlatformPermissions() {
        return () -> {
            Set<Permission> permissions = new LinkedHashSet<>();
            for (PlatformPermission platformPermission : PLATFORM_PERMISSIONS) {
                permissions.add(new Permission(
                        new PermissionCode(platformPermission.code()),
                        platformPermission.mutating() ? "write" : "read",
                        REQUEST_RESOURCE,
                        platformPermission.mutating() ? RiskTier.MEDIUM : RiskTier.LOW,
                        AuditLevel.ON_DECISION,
                        false,
                        true));
            }
            return permissions;
        };
    }

    /** scope 恒等映射到同名平台权限；未注册的组合不授予（deny-by-default）。 */
    @Bean
    ScopePermissionCeiling ainerServerScopeCeiling() {
        return (scope, permission) -> {
            for (PlatformPermission platformPermission : PLATFORM_PERMISSIONS) {
                if (platformPermission.code().equals(scope)
                        && platformPermission.code().equals(permission.value())) {
                    return true;
                }
            }
            return false;
        };
    }

    /** 平台权限走 BINDING_REQUIRED：主体必须持有授予该权限的 live Binding；状态检查恒真。 */
    @Bean
    DomainAuthorizationPolicy ainerServerDomainPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                for (PlatformPermission platformPermission : PLATFORM_PERMISSIONS) {
                    if (platformPermission.code().equals(permission.value())) {
                        return GrantPath.BINDING_REQUIRED;
                    }
                }
                return null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject, PermissionCode permission,
                    ResourceRef resource, AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject, PermissionCode permission,
                    ResourceRef resource, AuthorizationContext context) {
                for (PlatformPermission platformPermission : PLATFORM_PERMISSIONS) {
                    if (platformPermission.code().equals(permission.value())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * 管理面白名单：只有配置声明的 SERVICE 主体（同时持 authorization.manage scope，
     * 由 GrantAdministrationGuard 强制）可以管理 Role/Binding。白名单为空时全部拒绝。
     *
     * <p>条目格式为 {@code <issuer>|<subjectId>} 复合键（与 configuration.md §7 的
     * 「精确声明可信 issuer + sub」一致）：issuer 与主体成对声明，防止单一 issuer 部署
     * 演进为多 issuer 后同名 sub 被误信。不含 {@code |} 分隔符的条目永不匹配（fail-closed）。
     */
    @Bean
    GrantAdministrationPolicy ainerServerGrantAdministrationPolicy(
            @Value("${ainer.authorization.trusted-managers:}") String trustedManagers) {
        Set<String> managers = new LinkedHashSet<>();
        for (String manager : trustedManagers.split(",")) {
            String entry = manager.strip();
            if (!entry.isEmpty() && entry.indexOf('|') > 0) {
                managers.add(entry);
            }
        }
        return new GrantAdministrationPolicy() {
            @Override
            public String version() {
                return "ainer-server-reference-v1";
            }

            @Override
            public boolean isTrustedManager(AuthenticatedPrincipal actor) {
                return actor.isService() && managers.contains(
                        actor.authority().issuer() + "|" + actor.subjectId());
            }

            @Override
            public boolean isPermissionAssignable(AuthenticatedPrincipal actor, Permission permission) {
                return PLATFORM_PERMISSIONS.stream().anyMatch(
                        platformPermission -> platformPermission.code()
                                .equals(permission.code().value()));
            }

            @Override
            public boolean isScopeAssignable(AuthenticatedPrincipal actor, Scope scope) {
                return scope instanceof Scope.Workspace || scope instanceof Scope.Resource;
            }

            @Override
            public boolean isTargetAssignable(AuthenticatedPrincipal actor, SubjectRef target) {
                return target.type() == SubjectType.USER;
            }
        };
    }

    /**
     * 启动时把平台权限目录同步进 {@code ainer_authorization_permission} 管理投影（幂等
     * upsert）。决策权威是内存 PermissionRegistry；目录表只服务 Role 管理的外键与管理面
     * 查询——没有它，管理 API 创建 Role 会因外键失败。
     *
     * <p>同步源是容器内全部 {@link PermissionContributor} 的并集，与 PermissionRegistry
     * 的内存权威同源：新增模块贡献者时目录自动跟进，不再依赖手工维护的硬编码清单。
     */
    @Bean
    ApplicationRunner ainerServerPermissionCatalogSync(
            List<PermissionContributor> contributors,
            PermissionCatalogRepository catalogRepository) {
        return args -> contributors.stream()
                .flatMap(contributor -> contributor.contribute().stream())
                .forEach(permission -> catalogRepository.upsert(permission, "ainer-server"));
    }
}
