package dev.ainer.authorization;

import dev.ainer.authorization.catalog.PermissionRegistry;
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
import dev.ainer.authorization.domain.PublicProjection;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {

    private static final PermissionCode READ = new PermissionCode("doc.read");
    private static final PermissionCode PUBLISH = new PermissionCode("doc.publish");
    private static final PermissionCode SYS_ADMIN = new PermissionCode("sys.admin");
    private static final ResourceType DOC = new ResourceType("doc");
    private static final ResourceType TENANT = new ResourceType("tenant");
    private static final String DOCS_SCOPE = "docs";
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    private final UUID tenant = UUID.fromString("019c1000-0000-7000-8000-000000000001");
    private final UUID publicDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000a0");
    private final UUID ownedDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000b0");
    private final UUID otherDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000c0");
    private final SubjectRef user = new SubjectRef("ainer", "user-1", SubjectType.USER);
    private final SubjectRef service = new SubjectRef("ainer", "svc-1", SubjectType.SERVICE);

    private final PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
            new Permission(READ, "read", DOC, RiskTier.LOW, AuditLevel.ON_DECISION, false, false),
            new Permission(PUBLISH, "publish", DOC, RiskTier.HIGH, AuditLevel.ALWAYS, false, true),
            new Permission(SYS_ADMIN, "admin", TENANT, RiskTier.HIGH, AuditLevel.ALWAYS, true, false)));

    private AuthorizationService service(PublicAccessPolicy publicPolicy, DomainAuthorizationPolicy domainPolicy,
                                         BindingResolver bindings) {
        return new AuthorizationService(
                registry,
                (ScopePermissionCeiling) (scope, permission) -> DOCS_SCOPE.equals(scope),
                publicPolicy,
                domainPolicy,
                bindings,
                "test-s0");
    }

    private AuthorizationDecision auth(AuthorizationService svc, Requester requester, AccessMode mode,
                                       PermissionCode permission, ResourceType resourceType, UUID docId) {
        return auth(svc, requester, mode, permission, resourceType, docId,
                AuthorizationContext.Assurance.RECENT_STRONG);
    }

    private AuthorizationDecision auth(AuthorizationService svc, Requester requester, AccessMode mode,
                                       PermissionCode permission, ResourceType resourceType, UUID docId,
                                       AuthorizationContext.Assurance assurance) {
        return svc.authorize(new AuthorizationRequest(
                requester, mode, permission, new ResourceRef(tenant, resourceType, docId),
                new AuthorizationContext(NOW, assurance, null, null, null)));
    }

    private Requester.Authenticated authenticated(SubjectRef ref) {
        return new Requester.Authenticated(ref, tenant, Set.of(DOCS_SCOPE), Set.of("ainer"), "client-1");
    }

    // ---- P1 startup + basic pipeline ----

    @Test
    void unknownPermissionDenies() {
        var svc = service((p, r) -> java.util.Optional.empty(), alwaysBinding(READ), none());
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, new PermissionCode("x"), DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void resourceTypeMismatchDenies() {
        var svc = service((p, r) -> java.util.Optional.empty(), alwaysBinding(READ), none());
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, TENANT, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void systemOnlyPermissionDeniesForUser() {
        var svc = service((p, r) -> java.util.Optional.empty(), alwaysBinding(SYS_ADMIN), none());
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, SYS_ADMIN, TENANT, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void globalScopeBindingDeniedForNonServiceSubject() {
        SubjectBinding globalBinding = new SubjectBinding(
                user, new Role("r", Set.of(READ)), new Scope.Global(),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L);
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(READ, true),
                subject -> Set.of(globalBinding));
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void bindingFromDifferentSubjectFilteredOut() {
        SubjectRef otherUser = new SubjectRef("ainer", "user-2", SubjectType.USER);
        SubjectBinding othersBinding = new SubjectBinding(
                otherUser, new Role("r", Set.of(READ)), new Scope.Resource(tenant, DOC, ownedDocId),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L);
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(READ, true),
                subject -> Set.of(othersBinding));
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void publicProjectionAllowsWhenPolicyPresentAndSkipsAuthenticated() {
        var svc = service((p, r) -> r.resourceId().equals(publicDocId) ? java.util.Optional.of(new PublicProjection("public")) : java.util.Optional.empty(), alwaysBinding(READ), none());
        assertThat(auth(svc, new Requester.Anonymous(), AccessMode.PUBLIC_PROJECTION, READ, DOC, publicDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void publicProjectionDeniesWithoutPolicy() {
        var svc = service((p, r) -> java.util.Optional.empty(), alwaysBinding(READ), none());
        assertThat(auth(svc, new Requester.Anonymous(), AccessMode.PUBLIC_PROJECTION, READ, DOC, publicDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void authenticatedDenyDoesNotFallBackToPublic() {
        var svc = service((p, r) -> java.util.Optional.of(new PublicProjection("public")), alwaysBinding(READ), none());
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, otherDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    // ---- P1-4: grant ∩ state intersection ----

    @Test
    void bindingRequiredWithStateDeniedDenies() {
        // P1-4 fix: BINDING_REQUIRED requires BOTH binding AND state; state DENIED → DENY.
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(READ, ownedDocId)));
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(READ, false), subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void bindingRequiredWithStateAllowedAllows() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(READ, ownedDocId)));
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(READ, true), subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void relationDerivedWithStateDeniedDenies() {
        var svc = service((p, r) -> java.util.Optional.empty(),
                policy(GrantPath.RELATION_DERIVED, r -> r.resourceId().equals(ownedDocId), false),
                none());
        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, READ, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    // ---- risk收口 ----

    @Test
    void highRiskChallengesWithoutRecentStrongAuth() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(PUBLISH, ownedDocId)));
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(PUBLISH, true),
                subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, PUBLISH, DOC, ownedDocId,
                AuthorizationContext.Assurance.NONE).outcome()).isEqualTo(AuthorizationOutcome.CHALLENGE);
    }

    @Test
    void highRiskAllowsWithRecentStrongAuth() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(PUBLISH, ownedDocId)));
        var svc = service((p, r) -> java.util.Optional.empty(), bindingRequiredWithState(PUBLISH, true),
                subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(user), AccessMode.AUTHENTICATED, PUBLISH, DOC, ownedDocId)
                .outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    // ---- helpers ----

    private DomainAuthorizationPolicy alwaysBinding(PermissionCode... codes) {
        return policy(GrantPath.BINDING_REQUIRED, r -> false, true);
    }

    private DomainAuthorizationPolicy bindingRequiredWithState(PermissionCode code, boolean state) {
        return policy(GrantPath.BINDING_REQUIRED, r -> false, state);
    }

    private DomainAuthorizationPolicy policy(GrantPath path, Predicate<ResourceRef> owns, boolean state) {
        return new DomainAuthorizationPolicy() {
            @Override
            public GrantPath pathFor(PermissionCode permission) {
                return path;
            }

            @Override
            public boolean relationGrants(Requester.Authenticated subject, PermissionCode permission,
                                          ResourceRef resource, AuthorizationContext context) {
                return owns.test(resource);
            }

            @Override
            public boolean resourceStateSatisfies(Requester.Authenticated subject, PermissionCode permission,
                                                  ResourceRef resource, AuthorizationContext context) {
                return state;
            }
        };
    }

    private BindingResolver none() {
        return subject -> Set.of();
    }

    private SubjectBinding binding(PermissionCode permission, UUID docId) {
        return new SubjectBinding(
                user, new Role("r", Set.of(permission)), new Scope.Resource(tenant, DOC, docId),
                BindingStatus.ACTIVE, NOW.minusSeconds(3600), null, 1L);
    }
}
