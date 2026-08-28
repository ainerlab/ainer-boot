package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 把宿主产品的完整策略与模块级贡献组合成一套决策策略。
 *
 * <p>兼容规则有意保守：宿主策略只要认领了权限，就同时拥有该权限的 scope、关系与资源状态
 * 语义；模块贡献者不能扩大或替换它。宿主未认领时必须恰好一个贡献者认领，多个贡献者冲突
 * 会抛出异常而不是任意选择。
 */
public final class AuthorizationPolicyComposition {

    private AuthorizationPolicyComposition() {
    }

    /** 组合 scope 天花板。 */
    public static ScopePermissionCeiling scopeCeiling(
            ScopePermissionCeiling host,
            DomainAuthorizationPolicy hostDomain,
            List<AuthorizationPolicyContributor> contributors) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(hostDomain, "hostDomain");
        List<AuthorizationPolicyContributor> stableContributors = List.copyOf(contributors);
        return (scope, permission) -> {
            if (hostDomain.pathFor(permission) != null) {
                return host.permits(scope, permission);
            }
            AuthorizationPolicyContributor contributor = resolve(stableContributors, permission);
            return contributor != null && contributor.scopePermits(scope, permission);
        };
    }

    /** 组合领域授权策略。 */
    public static DomainAuthorizationPolicy domainPolicy(
            DomainAuthorizationPolicy host,
            List<AuthorizationPolicyContributor> contributors) {
        Objects.requireNonNull(host, "host");
        List<AuthorizationPolicyContributor> stableContributors = List.copyOf(contributors);
        return new DomainAuthorizationPolicy() {
            @Override
            public @Nullable GrantPath pathFor(PermissionCode permission) {
                GrantPath hostPath = host.pathFor(permission);
                if (hostPath != null) {
                    return hostPath;
                }
                AuthorizationPolicyContributor contributor = resolve(stableContributors, permission);
                return contributor == null ? null : contributor.pathFor(permission);
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                if (host.pathFor(permission) != null) {
                    return host.relationGrants(subject, permission, resource, context);
                }
                AuthorizationPolicyContributor contributor = resolve(stableContributors, permission);
                return contributor != null
                        && contributor.relationGrants(subject, permission, resource, context);
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject,
                    PermissionCode permission,
                    ResourceRef resource,
                    AuthorizationContext context) {
                if (host.pathFor(permission) != null) {
                    return host.resourceStateSatisfies(subject, permission, resource, context);
                }
                AuthorizationPolicyContributor contributor = resolve(stableContributors, permission);
                return contributor != null
                        && contributor.resourceStateSatisfies(subject, permission, resource, context);
            }
        };
    }

    private static @Nullable AuthorizationPolicyContributor resolve(
            List<AuthorizationPolicyContributor> contributors,
            PermissionCode permission) {
        AuthorizationPolicyContributor selected = null;
        for (AuthorizationPolicyContributor contributor : contributors) {
            if (contributor.pathFor(permission) == null) {
                continue;
            }
            if (selected != null) {
                throw new IllegalStateException(
                        "Multiple authorization policy contributors claim permission '%s'"
                                .formatted(permission.value()));
            }
            selected = contributor;
        }
        return selected;
    }
}
