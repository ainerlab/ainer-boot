package dev.ainer.authorization.consumer;

import dev.ainer.authorization.DefaultQueryAuthorizationPlanner;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.QueryAuthorizationRequest;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Role;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden Consumer verification for ADR-0030 S3: a product module defines its own query-intent type,
 * query-constraint type, and a {@link dev.ainer.authorization.policy.QueryConstraintBuilder}, then
 * uses Ainer's {@link DefaultQueryAuthorizationPlanner} to produce a typed constraint that its
 * repository would apply to exclude unauthorized rows — all without modifying Ainer source.
 *
 * <p>This closes scaffold creation gate 8 (Permission/Role/Binding/Scope/Decision minimal closure
 * verified by an external Golden Consumer) for the collection-query dimension.
 */
class GoldenConsumerQueryPlanTest {

    private static final PermissionCode LISTING_LIST_READ = new PermissionCode("merchant.listing.list.read");
    private static final ResourceType LISTING = new ResourceType("merchant.listing");
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String MERCHANT_SCOPE = "merchant.listings";

    private final UUID workspaceA = UUID.fromString("019c1000-0000-7000-8000-000000000001");
    private final UUID workspaceB = UUID.fromString("019c1000-0000-7000-8000-000000000002");
    private final UUID listingInA = UUID.fromString("019c1000-0000-7000-8000-0000000000a1");
    private final SubjectRef operator = new SubjectRef("xq-platform", "operator-1", SubjectType.USER);
    private final SubjectRef customer = new SubjectRef("xq-platform", "customer-1", SubjectType.USER);
    private final SubjectRef service = new SubjectRef("xq-platform", "svc-internal", SubjectType.SERVICE);

    /**
     * Product-defined query intent: the merchant operator wants to list their own listings in a
     * specific workspace, optionally filtered by status.
     */
    record ListingQueryIntent(UUID workspaceId, Set<String> statuses) {
    }

    /**
     * Product-defined typed query constraint that the repository would translate to parameterized SQL.
     * In a real consumer this would contain allowedWorkspaceIds, allowedResourceIds, publicOnly, etc.
     */
    record ListingReadConstraint(
            boolean global,
            Set<UUID> allowedWorkspaceIds,
            Set<UUID> allowedResourceIds) {

        static ListingReadConstraint empty() {
            return new ListingReadConstraint(false, new HashSet<>(), new HashSet<>());
        }
    }

    /**
     * Product-supplied {@link dev.ainer.authorization.policy.QueryConstraintBuilder} that accumulates
     * Ainer bindings into a {@link ListingReadConstraint}.
     */
    static final dev.ainer.authorization.policy.QueryConstraintBuilder<ListingReadConstraint> LISTING_CONSTRAINT_BUILDER =
            (current, binding, permission, resourceType) -> {
                ListingReadConstraint c = current != null ? current : ListingReadConstraint.empty();
                return switch (binding.scope()) {
                    case Scope.Global ignored -> new ListingReadConstraint(true, c.allowedWorkspaceIds(), c.allowedResourceIds());
                    case Scope.Workspace ws -> {
                        Set<UUID> ids = new HashSet<>(c.allowedWorkspaceIds());
                        ids.add(ws.workspaceId());
                        yield new ListingReadConstraint(c.global(), ids, c.allowedResourceIds());
                    }
                    case Scope.Resource res -> {
                        Set<UUID> ids = new HashSet<>(c.allowedResourceIds());
                        ids.add(res.resourceId());
                        yield new ListingReadConstraint(c.global(), c.allowedWorkspaceIds(), ids);
                    }

                    // 注：Workspace 字段属于 Resource scope 的归属信息（满足 DB CHECK 三列非空），
                    //     查询约束累积器只关心 resourceId 本身。
                };
            };

    @Test
    void operatorWithWorkspaceBindingGetsConstrainedQueryPlan() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                operatorBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = queryRequest(operator, LISTING_LIST_READ,
                new ListingQueryIntent(workspaceA, Set.of("PUBLISHED")));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Allowed.class);
        AuthorizedQueryPlan.Allowed<ListingReadConstraint> allowed = (AuthorizedQueryPlan.Allowed<ListingReadConstraint>) plan;
        assertThat(allowed.constraint().global()).isFalse();
        assertThat(allowed.constraint().allowedWorkspaceIds()).containsExactly(workspaceA);
    }

    @Test
    void operatorWithResourceBindingGetsSpecificResourceConstraint() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                resourceBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = queryRequest(operator, LISTING_LIST_READ,
                new ListingQueryIntent(workspaceA, Set.of()));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Allowed.class);
        AuthorizedQueryPlan.Allowed<ListingReadConstraint> allowed = (AuthorizedQueryPlan.Allowed<ListingReadConstraint>) plan;
        assertThat(allowed.constraint().allowedResourceIds()).containsExactly(listingInA);
    }

    @Test
    void serviceWithGlobalBindingGetsUnconstrainedPlan() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                serviceBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = queryRequest(service, LISTING_LIST_READ,
                new ListingQueryIntent(workspaceA, Set.of()));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Allowed.class);
        AuthorizedQueryPlan.Allowed<ListingReadConstraint> allowed = (AuthorizedQueryPlan.Allowed<ListingReadConstraint>) plan;
        assertThat(allowed.constraint().global()).isTrue();
    }

    @Test
    void customerWithoutBindingIsDenied() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                operatorBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = queryRequest(customer, LISTING_LIST_READ,
                new ListingQueryIntent(workspaceA, Set.of()));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Denied.class);
    }

    @Test
    void revokedBindingExcludedFromQueryPlan() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                revokedBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = queryRequest(operator, LISTING_LIST_READ,
                new ListingQueryIntent(workspaceA, Set.of()));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        // Revoked binding → no contributing bindings → denied
        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Denied.class);
    }

    @Test
    void wrongScopeCeilingDenied() {
        DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> planner = buildPlanner(
                operatorBindings());

        QueryAuthorizationRequest<ListingQueryIntent> request = new QueryAuthorizationRequest<>(
                new Requester.Authenticated(operator, Set.of("wrong-scope"), Set.of("xq-platform"), "xq-shop-next"),
                AccessMode.AUTHENTICATED, LISTING_LIST_READ, LISTING, "merchant-listing-search",
                new ListingQueryIntent(workspaceA, Set.of()),
                new AuthorizationContext(NOW, AuthorizationContext.Assurance.RECENT_STRONG, "xq-shop-next", null, null));

        AuthorizedQueryPlan<ListingReadConstraint> plan = planner.plan(request);

        assertThat(plan).isInstanceOf(AuthorizedQueryPlan.Denied.class);
    }

    // ---- fixtures ----

    private DefaultQueryAuthorizationPlanner<ListingQueryIntent, ListingReadConstraint> buildPlanner(
            BindingResolver resolver) {
        PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
                new Permission(LISTING_LIST_READ, "list-read", LISTING, RiskTier.LOW, AuditLevel.NONE, false, false)));

        DomainAuthorizationPolicy policy = new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                if (LISTING_LIST_READ.equals(permission)) return GrantPath.BINDING_REQUIRED;
                return null;
            }

            @Override
            public boolean relationGrants(Requester.Authenticated subject, PermissionCode permission,
                                          dev.ainer.authorization.domain.ResourceRef resource, AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(Requester.Authenticated subject, PermissionCode permission,
                                                  dev.ainer.authorization.domain.ResourceRef resource, AuthorizationContext context) {
                return true;
            }
        };

        return new DefaultQueryAuthorizationPlanner<>(
                registry,
                (ScopePermissionCeiling) (scope, perm) -> MERCHANT_SCOPE.equals(scope),
                resolver,
                policy,
                LISTING_CONSTRAINT_BUILDER,
                "golden-consumer-s3");
    }

    private QueryAuthorizationRequest<ListingQueryIntent> queryRequest(
            SubjectRef subject, PermissionCode permission, ListingQueryIntent intent) {
        return new QueryAuthorizationRequest<>(
                new Requester.Authenticated(subject, Set.of(MERCHANT_SCOPE), Set.of("xq-platform"), "xq-shop-next"),
                AccessMode.AUTHENTICATED, permission, LISTING, "merchant-listing-search",
                intent,
                new AuthorizationContext(NOW, AuthorizationContext.Assurance.RECENT_STRONG, "xq-shop-next", null, null));
    }

    private BindingResolver operatorBindings() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(operator, Set.of(new SubjectBinding(
                operator,
                new Role("merchant-operator", "Merchant Operator", Set.of(LISTING_LIST_READ)),
                new Scope.Workspace(workspaceA),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L)));
        return subject -> table.getOrDefault(subject, Set.of());
    }

    private BindingResolver resourceBindings() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(operator, Set.of(new SubjectBinding(
                operator,
                new Role("merchant-operator", "Merchant Operator", Set.of(LISTING_LIST_READ)),
                new Scope.Resource(workspaceA, LISTING, listingInA),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L)));
        return subject -> table.getOrDefault(subject, Set.of());
    }

    private BindingResolver serviceBindings() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(service, Set.of(new SubjectBinding(
                service,
                new Role("platform-reader", "Platform Reader", Set.of(LISTING_LIST_READ)),
                new Scope.Global(),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L)));
        return subject -> table.getOrDefault(subject, Set.of());
    }

    private BindingResolver revokedBindings() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(operator, Set.of(new SubjectBinding(
                operator,
                new Role("merchant-operator", "Merchant Operator", Set.of(LISTING_LIST_READ)),
                new Scope.Workspace(workspaceA),
                BindingStatus.REVOKED, NOW.minusSeconds(3600), null, 1L)));
        return subject -> table.getOrDefault(subject, Set.of());
    }
}
