package dev.ainer.authorization.consumer;

import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.catalog.PolicyRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Role;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulates an EXTERNAL consumer (e.g., xq-platform-next) that defines its own product Permissions,
 * registers them with Ainer's authorization catalog, provides its own policy/facts/binding resolver,
 * and performs real authorization judgments — all without modifying Ainer source or constants.
 * This is the Golden Consumer verification for ADR-0030 S0 gate 8.
 */
class GoldenConsumerAuthorizationTest {

    private static final PermissionCode LISTING_PUBLISH = new PermissionCode("merchant.listing.publish");
    private static final PermissionCode LISTING_READ = new PermissionCode("merchant.listing.read");
    private static final ResourceType LISTING = new ResourceType("merchant.listing");
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String MERCHANT_SCOPE = "merchant.listings";

    private final UUID workspace = UUID.fromString("019c1000-0000-7000-8000-0000000000a1");
    private final UUID listingId = UUID.fromString("019c1000-0000-7000-8000-0000000000b1");
    private final UUID otherListingId = UUID.fromString("019c1000-0000-7000-8000-0000000000c1");
    private final SubjectRef merchantOperator = new SubjectRef("xq-platform", "operator-1", SubjectType.USER);
    private final SubjectRef customer = new SubjectRef("xq-platform", "customer-1", SubjectType.USER);

    @Test
    void goldenConsumerDefinesProductPermissionsAndAuthorizes() {
        PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
                new Permission(LISTING_PUBLISH, "publish", LISTING, RiskTier.HIGH, AuditLevel.ALWAYS, false, true),
                new Permission(LISTING_READ, "read", LISTING, RiskTier.LOW, AuditLevel.ON_DECISION, false, false)));

        DomainAuthorizationPolicy policy = consumerPolicy();
        BindingResolver bindings = consumerBindings();

        new PolicyRegistry().validate(registry, policy);

        AuthorizationService svc = new AuthorizationService(
                registry,
                (ScopePermissionCeiling) (scope, perm) -> MERCHANT_SCOPE.equals(scope),
                (PublicAccessPolicy) (perm, res) -> Optional.empty(),
                policy,
                bindings,
                "golden-consumer-s0");

        // Operator with binding + scope + recent strong auth → ALLOW publish
        AuthorizationDecision publish = authorize(svc, merchantOperator, MERCHANT_SCOPE, LISTING_PUBLISH, listingId,
                AuthorizationContext.Assurance.RECENT_STRONG);
        assertThat(publish.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);

        // Operator without scope ceiling → DENY
        AuthorizationDecision noScope = authorize(svc, merchantOperator, "wrong-scope", LISTING_PUBLISH, listingId,
                AuthorizationContext.Assurance.RECENT_STRONG);
        assertThat(noScope.outcome()).isEqualTo(AuthorizationOutcome.DENY);

        // Operator on wrong listing → DENY (scope mismatch)
        AuthorizationDecision wrongListing = authorize(svc, merchantOperator, MERCHANT_SCOPE, LISTING_PUBLISH, otherListingId,
                AuthorizationContext.Assurance.RECENT_STRONG);
        assertThat(wrongListing.outcome()).isEqualTo(AuthorizationOutcome.DENY);

        // HIGH risk without strong auth → CHALLENGE
        AuthorizationDecision challenge = authorize(svc, merchantOperator, MERCHANT_SCOPE, LISTING_PUBLISH, listingId,
                AuthorizationContext.Assurance.NONE);
        assertThat(challenge.outcome()).isEqualTo(AuthorizationOutcome.CHALLENGE);

        // Customer without binding → DENY publish
        AuthorizationDecision customerDeny = authorize(svc, customer, MERCHANT_SCOPE, LISTING_PUBLISH, listingId,
                AuthorizationContext.Assurance.RECENT_STRONG);
        assertThat(customerDeny.outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    private AuthorizationDecision authorize(AuthorizationService svc, SubjectRef subject, String scope,
                                            PermissionCode permission, UUID resource, AuthorizationContext.Assurance assurance) {
        return svc.authorize(new AuthorizationRequest(
                new Requester.Authenticated(subject, Set.of(scope), Set.of("xq-platform"), "xq-shop-next"),
                AccessMode.AUTHENTICATED, permission, new ResourceRef(workspace, LISTING, resource),
                new AuthorizationContext(NOW, assurance, "xq-shop-next", null, null)));
    }

    private DomainAuthorizationPolicy consumerPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                if (LISTING_PUBLISH.equals(permission)) return GrantPath.BINDING_REQUIRED;
                if (LISTING_READ.equals(permission)) return GrantPath.BINDING_OR_RELATION;
                return null;
            }

            @Override
            public boolean relationGrants(Requester.Authenticated subject, PermissionCode permission,
                                          ResourceRef resource, AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(Requester.Authenticated subject, PermissionCode permission,
                                                  ResourceRef resource, AuthorizationContext context) {
                return true;
            }
        };
    }

    private BindingResolver consumerBindings() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(merchantOperator, Set.of(new SubjectBinding(
                merchantOperator,
                new Role("merchant-operator", "Merchant Operator", Set.of(LISTING_PUBLISH, LISTING_READ)),
                new Scope.Resource(workspace, LISTING, listingId),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L)));
        return subject -> table.getOrDefault(subject, Set.of());
    }
}
