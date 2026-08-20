package dev.ainer.authorization;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ReasonCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectSetBinding;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.authorization.policy.SubjectSetMembership;
import dev.ainer.authorization.policy.SubjectSetMembershipRegistry;

import java.util.Objects;

/**
 * 纯授权决策求值器（ADR-0030 §6）。实现授权路径真值表：
 * <ul>
 *   <li>默认拒绝，不存在 PUBLIC→AUTHENTICATED 或 AUTHENTICATED→PUBLIC 的回退。</li>
 *   <li>资源类型检查：{@code Permission.resourceType} 必须与 {@code ResourceRef.resourceType} 匹配。</li>
 *   <li>systemOnly 强制：{@code systemOnly} 权限只允许 SERVICE 主体。</li>
 *   <li>Binding 归属防御：解析器返回的 Binding 必须属于发起请求的主体。</li>
 *   <li>GLOBAL scope 仅限 SERVICE 主体。</li>
 *   <li>授权 ∩ 状态交集：BINDING_REQUIRED = binding ∧ state；RELATION_DERIVED = relation ∧ state；
 *       BINDING_OR_RELATION = (binding ∨ relation) ∧ state。</li>
 *   <li>风险收口统一应用（包括 PUBLIC 路径）：HIGH 风险且缺少近期强认证时返回 CHALLENGE。</li>
 * </ul>
 */
public final class AuthorizationService {

    private final PermissionRegistry permissionRegistry;
    private final ScopePermissionCeiling scopeCeiling;
    private final PublicAccessPolicy publicAccessPolicy;
    private final DomainAuthorizationPolicy domainPolicy;
    private final BindingResolver bindingResolver;
    private final SubjectSetMembershipRegistry setMembershipRegistry;
    private final String policyVersion;

    /**
     * 不含集合成员关系的源码兼容构造器（ADR-0042 O2）：等价于不支持任何主体集合族——
     * 集合 Binding 永不贡献授权（fail-closed）。
     */
    public AuthorizationService(
            PermissionRegistry permissionRegistry,
            ScopePermissionCeiling scopeCeiling,
            PublicAccessPolicy publicAccessPolicy,
            DomainAuthorizationPolicy domainPolicy,
            BindingResolver bindingResolver,
            String policyVersion) {
        this(permissionRegistry, scopeCeiling, publicAccessPolicy, domainPolicy, bindingResolver,
                noSetMembership(), policyVersion);
    }

    public AuthorizationService(
            PermissionRegistry permissionRegistry,
            ScopePermissionCeiling scopeCeiling,
            PublicAccessPolicy publicAccessPolicy,
            DomainAuthorizationPolicy domainPolicy,
            BindingResolver bindingResolver,
            SubjectSetMembershipRegistry setMembershipRegistry,
            String policyVersion) {
        this.permissionRegistry = Objects.requireNonNull(permissionRegistry, "permissionRegistry");
        this.scopeCeiling = Objects.requireNonNull(scopeCeiling, "scopeCeiling");
        this.publicAccessPolicy = Objects.requireNonNull(publicAccessPolicy, "publicAccessPolicy");
        this.domainPolicy = Objects.requireNonNull(domainPolicy, "domainPolicy");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.setMembershipRegistry = Objects.requireNonNull(setMembershipRegistry, "setMembershipRegistry");
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    }

    private static SubjectSetMembershipRegistry noSetMembership() {
        return new SubjectSetMembershipRegistry() {
            @Override
            public boolean supports(dev.ainer.authorization.domain.SubjectSetRef set) {
                return false;
            }

            @Override
            public SubjectSetMembership membership(
                    dev.ainer.authorization.domain.SubjectRef requester,
                    dev.ainer.authorization.domain.SubjectSetRef set,
                    java.time.Instant evaluationTime) {
                return SubjectSetMembership.unavailable();
            }
        };
    }

    public AuthorizationDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return decide(request);
        } catch (RuntimeException unexpected) {
            return AuthorizationDecision.deny(
                    AuthorizationReasonCodes.UNEXPECTED, policyVersion, request.context().evaluatedAt());
        }
    }

    private AuthorizationDecision decide(AuthorizationRequest request) {
        Permission permission = permissionRegistry.find(request.permission()).orElse(null);
        if (permission == null) {
            return deny(request, AuthorizationReasonCodes.UNKNOWN_PERMISSION);
        }

        if (!permission.resourceType().equals(request.resource().resourceType())) {
            return deny(request, AuthorizationReasonCodes.RESOURCE_TYPE_MISMATCH);
        }

        // systemOnly 权限绝不能经由 PUBLIC 路径提供（ADR-0030 §3.1/§5.1）：
        // systemOnly 意味着"只允许受控 SERVICE 使用/管理"。对 systemOnly 权限发起的
        // PUBLIC_PROJECTION 请求无论 PublicAccessPolicy 如何裁决都一律拒绝。
        if (permission.systemOnly() && request.accessMode() == AccessMode.PUBLIC_PROJECTION) {
            return deny(request, AuthorizationReasonCodes.SYSTEM_ONLY);
        }

        if (request.accessMode() == AccessMode.PUBLIC_PROJECTION) {
            return decidePublic(request, permission);
        }
        return decideAuthenticated(request, permission);
    }

    private AuthorizationDecision decidePublic(AuthorizationRequest request, Permission permission) {
        var projection = publicAccessPolicy.evaluate(request.permission(), request.resource());
        if (projection.isEmpty()) {
            return deny(request, AuthorizationReasonCodes.NO_PUBLIC_POLICY);
        }
        if (permission.riskTier() == RiskTier.HIGH
                && request.context().assurance() != AuthorizationContext.Assurance.RECENT_STRONG) {
            return AuthorizationDecision.challengeAuthentication(
                    AuthorizationReasonCodes.STRONG_AUTH_REQUIRED, policyVersion, request.context().evaluatedAt());
        }
        return AuthorizationDecision.allowPublic(
                AuthorizationReasonCodes.PUBLIC_ALLOWED, policyVersion,
                request.context().evaluatedAt(), projection.get());
    }

    private AuthorizationDecision decideAuthenticated(AuthorizationRequest request, Permission permission) {
        if (!(request.requester() instanceof Requester.Authenticated subject)) {
            return deny(request, AuthorizationReasonCodes.AUTHENTICATED_REQUIRED);
        }

        if (permission.systemOnly() && subject.subjectRef().type() != SubjectType.SERVICE) {
            return deny(request, AuthorizationReasonCodes.SYSTEM_ONLY);
        }

        boolean scopeOk = subject.scopeCeiling().stream()
                .anyMatch(scope -> scopeCeiling.permits(scope, request.permission()));
        if (!scopeOk) {
            return deny(request, AuthorizationReasonCodes.SCOPE_CEILING);
        }

        GrantPath path = domainPolicy.pathFor(request.permission());
        if (path == null) {
            return deny(request, AuthorizationReasonCodes.UNKNOWN_POLICY);
        }

        boolean bindingGrant = bindingResolver.liveBindings(subject.subjectRef()).stream()
                .filter(b -> b.subject().equals(subject.subjectRef()))
                .filter(b -> !(b.scope() instanceof Scope.Global)
                        || subject.subjectRef().type() == SubjectType.SERVICE)
                .anyMatch(b -> b.isLive(request.permission(), request.resource(), request.context().evaluatedAt()));

        boolean setGrant = setBindingGrant(subject, request);

        boolean relationGrant = domainPolicy.relationGrants(
                subject, request.permission(), request.resource(), request.context());

        boolean stateOk = domainPolicy.resourceStateSatisfies(
                subject, request.permission(), request.resource(), request.context());

        boolean granted = switch (path) {
            case BINDING_REQUIRED -> (bindingGrant || setGrant) && stateOk;
            case RELATION_DERIVED -> relationGrant && stateOk;
            case BINDING_OR_RELATION -> (bindingGrant || setGrant || relationGrant) && stateOk;
        };

        if (!granted) {
            return deny(request, path == GrantPath.RELATION_DERIVED
                    ? AuthorizationReasonCodes.NO_RELATION
                    : !stateOk ? AuthorizationReasonCodes.STATE_DENIED
                    : AuthorizationReasonCodes.NO_BINDING);
        }

        if (permission.riskTier() == RiskTier.HIGH
                && request.context().assurance() != AuthorizationContext.Assurance.RECENT_STRONG) {
            return AuthorizationDecision.challengeAuthentication(
                    AuthorizationReasonCodes.STRONG_AUTH_REQUIRED, policyVersion, request.context().evaluatedAt());
        }
        return allow(request, AuthorizationReasonCodes.AUTHORIZED);
    }

    /**
     * 主体集合授权路径（ADR-0042 O2）：一条 live 且 scope 覆盖资源的集合 Binding，
     * 只有当请求主体在决策时刻是该集合的 MEMBER 时才贡献授权（pull 式，无缓存）。
     * GLOBAL scope 绝不搭载集合 Binding（创建时已拒绝，这里防御性再次排除）。
     */
    private boolean setBindingGrant(Requester.Authenticated subject, AuthorizationRequest request) {
        java.time.Instant at = request.context().evaluatedAt();
        for (SubjectSetBinding binding : bindingResolver.liveSetBindings(request.resource(), at)) {
            if (binding.scope() instanceof Scope.Global) {
                continue;
            }
            if (binding.isLive(request.permission(), request.resource(), at)) {
                SubjectSetMembership membership =
                        setMembershipRegistry.membership(subject.subjectRef(), binding.set(), at);
                if (membership.isMember()) {
                    return true;
                }
            }
        }
        return false;
    }

    private AuthorizationDecision allow(AuthorizationRequest request, ReasonCode reason) {
        return AuthorizationDecision.allow(reason, policyVersion, request.context().evaluatedAt());
    }

    private AuthorizationDecision deny(AuthorizationRequest request, ReasonCode reason) {
        return AuthorizationDecision.deny(reason, policyVersion, request.context().evaluatedAt());
    }
}
