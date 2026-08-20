package dev.ainer.authorization;

import dev.ainer.authorization.AuthorizationReasonCodes;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.QueryAuthorizationRequest;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.authorization.policy.QueryAuthorizationPlanner;
import dev.ainer.authorization.policy.QueryConstraintBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link QueryAuthorizationPlanner} 的默认实现（ADR-0030 §7、S3）。
 *
 * <p>求值 scope ceiling、权限注册表与 live Binding，产出类型化查询约束 {@code Q}。
 * 产品适配器负责把 {@code Q} 翻译为参数化 SQL/检索过滤条件——Ainer 绝不输出 SQL。
 * 若没有任何 Binding 授予所请求的权限，则整个查询被拒绝。
 *
 * @param <I> 产品定义的查询意图类型
 * @param <Q> 产品定义的类型化查询约束
 */
public final class DefaultQueryAuthorizationPlanner<I, Q> implements QueryAuthorizationPlanner<I, Q> {

    private final PermissionRegistry permissionRegistry;
    private final ScopePermissionCeiling scopeCeiling;
    private final BindingResolver bindingResolver;
    private final DomainAuthorizationPolicy domainPolicy;
    private final QueryConstraintBuilder<Q> constraintBuilder;
    private final String policyVersion;

    public DefaultQueryAuthorizationPlanner(
            PermissionRegistry permissionRegistry,
            ScopePermissionCeiling scopeCeiling,
            BindingResolver bindingResolver,
            DomainAuthorizationPolicy domainPolicy,
            QueryConstraintBuilder<Q> constraintBuilder,
            String policyVersion) {
        this.permissionRegistry = Objects.requireNonNull(permissionRegistry, "permissionRegistry");
        this.scopeCeiling = Objects.requireNonNull(scopeCeiling, "scopeCeiling");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.domainPolicy = Objects.requireNonNull(domainPolicy, "domainPolicy");
        this.constraintBuilder = Objects.requireNonNull(constraintBuilder, "constraintBuilder");
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    }

    @Override
    public AuthorizedQueryPlan<Q> plan(QueryAuthorizationRequest<I> request) {
        if (request.accessMode() == AccessMode.PUBLIC_PROJECTION) {
            // PUBLIC 查询由产品的 PublicAccessPolicy 在适配器层处理；
            // 第一版查询规划器只服务 AUTHENTICATED 集合查询。
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.AUTHENTICATED_REQUIRED.value(), policyVersion);
        }

        if (!(request.requester() instanceof Requester.Authenticated subject)) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.AUTHENTICATED_REQUIRED.value(), policyVersion);
        }

        PermissionCode permission = request.permission();
        Permission perm = permissionRegistry.find(permission).orElse(null);
        if (perm == null) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.UNKNOWN_PERMISSION.value(), policyVersion);
        }

        // 资源类型不匹配：权限面向的是另一种资源类型。
        if (!perm.resourceType().equals(request.resourceType())) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.RESOURCE_TYPE_MISMATCH.value(), policyVersion);
        }

        // systemOnly 权限要求 SERVICE 主体。
        if (perm.systemOnly() && subject.subjectRef().type() != SubjectType.SERVICE) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.SYSTEM_ONLY.value(), policyVersion);
        }

        // scope ceiling 校验。
        boolean hasScope = subject.scopeCeiling().stream()
                .anyMatch(s -> scopeCeiling.permits(s, permission));
        if (!hasScope) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.SCOPE_CEILING.value(), policyVersion);
        }

        // 授权路径：只有 BINDING_REQUIRED 与 BINDING_OR_RELATION 支持集合查询
        //（RELATION_DERIVED 查询需要逐资源事实，违背查询规划的目的）。
        GrantPath path = domainPolicy.pathFor(permission);
        if (path == null) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.UNKNOWN_POLICY.value(), policyVersion);
        }
        if (path == GrantPath.RELATION_DERIVED) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.NO_BINDING.value(), policyVersion);
        }

        // 收集授予该权限的 live Binding 并累加查询约束。
        Set<SubjectBinding> liveBindings = bindingResolver.liveBindings(subject.subjectRef());
        @Nullable Q constraint = null;
        int contributingBindings = 0;
        for (SubjectBinding binding : liveBindings) {
            if (!canContribute(binding, subject, request)) {
                continue;
            }
            constraint = Objects.requireNonNull(
                    constraintBuilder.accumulate(
                            constraint, binding, permission, request.resourceType()),
                    "query constraint builder returned null");
            contributingBindings++;
        }

        if (contributingBindings == 0) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.NO_BINDING.value(), policyVersion);
        }

        return new AuthorizedQueryPlan.Allowed<>(constraint, List.of(), policyVersion);
    }

    private boolean canContribute(
            SubjectBinding binding,
            Requester.Authenticated requester,
            QueryAuthorizationRequest<I> request) {
        if (!binding.subject().equals(requester.subjectRef())
                || binding.status() != BindingStatus.ACTIVE
                || !binding.role().grants(request.permission())) {
            return false;
        }

        if (request.context().evaluatedAt().isBefore(binding.validFrom())
                || (binding.validUntil() != null
                && !request.context().evaluatedAt().isBefore(binding.validUntil()))) {
            return false;
        }

        if (binding.scope() instanceof Scope.Global
                && requester.subjectRef().type() != SubjectType.SERVICE) {
            return false;
        }

        return !(binding.scope() instanceof Scope.Resource resourceScope)
                || resourceScope.resourceType().equals(request.resourceType());
    }
}
