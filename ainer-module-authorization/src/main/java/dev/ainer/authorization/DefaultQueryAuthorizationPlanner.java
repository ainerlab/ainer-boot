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
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.authorization.policy.QueryAuthorizationPlanner;
import dev.ainer.authorization.policy.QueryConstraintBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of {@link QueryAuthorizationPlanner} (ADR-0030 §7, S3).
 *
 * <p>Evaluates scope ceiling, permission registry and live bindings to produce a typed query
 * constraint {@code Q}. The product adapter translates {@code Q} to parameterized SQL/search
 * filters — Ainer never outputs SQL. If no binding grants the requested permission, the query
 * is denied.
 *
 * @param <I> product-defined query-intent type
 * @param <Q> product-defined typed query constraint
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
            // PUBLIC queries are handled by the product's PublicAccessPolicy at the adapter level;
            // the query planner only serves AUTHENTICATED collection queries in the first version.
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

        // Resource type mismatch: the permission targets a different resource type.
        if (!perm.resourceType().equals(request.resourceType())) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.RESOURCE_TYPE_MISMATCH.value(), policyVersion);
        }

        // System-only permissions require SERVICE.
        if (perm.systemOnly() && subject.subjectRef().type() != dev.ainer.authorization.domain.SubjectType.SERVICE) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.SYSTEM_ONLY.value(), policyVersion);
        }

        // Scope ceiling.
        boolean hasScope = subject.scopeCeiling().stream()
                .anyMatch(s -> scopeCeiling.permits(s, permission));
        if (!hasScope) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.SCOPE_CEILING.value(), policyVersion);
        }

        // Grant path: only BINDING_REQUIRED and BINDING_OR_RELATION paths support collection queries
        // (RELATION_DERIVED queries require per-resource facts, which defeats the purpose of query planning).
        GrantPath path = domainPolicy.pathFor(permission);
        if (path == null) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.UNKNOWN_POLICY.value(), policyVersion);
        }
        if (path == GrantPath.RELATION_DERIVED) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.NO_BINDING.value(), policyVersion);
        }

        // Collect live bindings that grant this permission and accumulate the constraint.
        Set<SubjectBinding> liveBindings = bindingResolver.liveBindings(subject.subjectRef());
        Q constraint = null;
        int contributingBindings = 0;
        for (SubjectBinding binding : liveBindings) {
            if (!binding.role().grants(permission)) {
                continue;
            }
            if (binding.status() != BindingStatus.ACTIVE) {
                continue;
            }
            constraint = constraintBuilder.accumulate(constraint, binding, permission, request.resourceType());
            contributingBindings++;
        }

        if (contributingBindings == 0) {
            return new AuthorizedQueryPlan.Denied<>(
                    AuthorizationReasonCodes.NO_BINDING.value(), policyVersion);
        }

        return new AuthorizedQueryPlan.Allowed<>(constraint, List.of(), policyVersion);
    }
}
