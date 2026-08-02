package dev.ainer.authorization;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.BindingStatus;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Role;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.RelationOutcome;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {

    private static final PermissionCode READ = new PermissionCode("doc.read");
    private static final PermissionCode PUBLISH = new PermissionCode("doc.publish");
    private static final ResourceType DOC = new ResourceType("doc");
    private static final String DOCS_SCOPE = "docs";
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    private final UUID tenant = UUID.fromString("019c1000-0000-7000-8000-000000000001");
    private final UUID publicDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000a0");
    private final UUID ownedDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000b0");
    private final UUID otherDocId = UUID.fromString("019c1000-0000-7000-8000-0000000000c0");
    private final SubjectRef user = new SubjectRef("ainer", "user-1", SubjectType.USER);

    private final PermissionRegistry registry = new PermissionRegistry().register(() -> Set.of(
            new Permission(READ, "read", DOC, RiskTier.LOW, AuditLevel.ON_DECISION, false, false),
            new Permission(PUBLISH, "publish", DOC, RiskTier.HIGH, AuditLevel.ALWAYS, false, true)));

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
                                       PermissionCode permission, UUID docId) {
        return auth(svc, requester, mode, permission, docId, AuthorizationContext.Assurance.RECENT_STRONG);
    }

    private AuthorizationDecision auth(AuthorizationService svc, Requester requester, AccessMode mode,
                                       PermissionCode permission, UUID docId, AuthorizationContext.Assurance assurance) {
        return svc.authorize(new AuthorizationRequest(
                requester, mode, permission, new ResourceRef(tenant, DOC, docId),
                new AuthorizationContext(NOW, assurance, null, null, null)));
    }

    private Requester.Authenticated authenticated(UUID credentialTenant, boolean withScope) {
        return new Requester.Authenticated(
                user, credentialTenant, withScope ? Set.of(DOCS_SCOPE) : Set.of(), Set.of("ainer"), "client-1");
    }

    @Test
    void unknownPermissionDenies() {
        AuthorizationService svc = service((p, r) -> false, pathOnly(READ), none());

        AuthorizationDecision d = auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED,
                new PermissionCode("doc.unknown"), ownedDocId);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void publicProjectionAllowsWhenPolicyPresentAndSkipsAuthenticated() {
        AuthorizationService svc = service((p, r) -> r.resourceId().equals(publicDocId), pathOnly(READ), none());

        AuthorizationDecision d = auth(svc, new Requester.Anonymous(), AccessMode.PUBLIC_PROJECTION, READ, publicDocId);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void publicProjectionDeniesWithoutPolicy() {
        AuthorizationService svc = service((p, r) -> false, pathOnly(READ), none());

        AuthorizationDecision d = auth(svc, new Requester.Anonymous(), AccessMode.PUBLIC_PROJECTION, READ, publicDocId);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void authenticatedAnonymousAndScopeAndTenantCeilingsDeny() {
        AuthorizationService svc = service((p, r) -> true, pathOnly(READ), none());

        assertThat(auth(svc, new Requester.Anonymous(), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
        assertThat(auth(svc, authenticated(tenant, false), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
        UUID otherTenant = UUID.fromString("019c1000-0000-7000-8000-000000000099");
        assertThat(auth(svc, authenticated(otherTenant, true), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void bindingRequiredAllowsWithLiveBindingAndDeniesWithout() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(READ, ownedDocId)));
        AuthorizationService svc = service((p, r) -> false, pathOnly(READ, PUBLISH), subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, otherDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void relationDerivedAllowsForOwnedResourceOnly() {
        AuthorizationService svc = service(
                (p, r) -> false,
                relationPolicy(READ, r -> r.resourceId().equals(ownedDocId)),
                none());

        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, otherDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void bindingOrRelationAllowsOnEitherCompleteBranch() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(READ, ownedDocId)));
        AuthorizationService svc = service(
                (p, r) -> false,
                eitherPolicy(READ, r -> r.resourceId().equals(otherDocId)),
                subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, otherDocId).outcome())
                .isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void highRiskChallengesWithoutRecentStrongAuth() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(PUBLISH, ownedDocId)));
        AuthorizationService svc = service((p, r) -> false, pathOnly(PUBLISH), subject -> table.getOrDefault(subject, Set.of()));

        AuthorizationDecision d = auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED,
                PUBLISH, ownedDocId, AuthorizationContext.Assurance.NONE);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.CHALLENGE);
    }

    @Test
    void highRiskAllowsWithRecentStrongAuth() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(binding(PUBLISH, ownedDocId)));
        AuthorizationService svc = service((p, r) -> false, pathOnly(PUBLISH), subject -> table.getOrDefault(subject, Set.of()));

        AuthorizationDecision d = auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, PUBLISH, ownedDocId);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void authenticatedDenyDoesNotFallBackToPublic() {
        AuthorizationService svc = service((p, r) -> true, pathOnly(READ), none());

        AuthorizationDecision d = auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, otherDocId);

        assertThat(d.outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void revokedBindingDeniesEvenWithScope() {
        Map<SubjectRef, Set<SubjectBinding>> table = new HashMap<>();
        table.put(user, Set.of(new SubjectBinding(
                user, new Role("r", Set.of(READ)), new Scope.Resource(tenant, DOC, ownedDocId),
                BindingStatus.REVOKED, NOW.minusSeconds(60), null, 1L)));
        AuthorizationService svc = service((p, r) -> false, pathOnly(READ), subject -> table.getOrDefault(subject, Set.of()));

        assertThat(auth(svc, authenticated(tenant, true), AccessMode.AUTHENTICATED, READ, ownedDocId).outcome())
                .isEqualTo(AuthorizationOutcome.DENY);
    }

    private DomainAuthorizationPolicy pathOnly(PermissionCode... codes) {
        Map<PermissionCode, dev.ainer.authorization.domain.GrantPath> paths = new HashMap<>();
        for (PermissionCode c : codes) {
            paths.put(c, dev.ainer.authorization.domain.GrantPath.BINDING_REQUIRED);
        }
        return new DomainAuthorizationPolicy() {
            @Override
            public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                return paths.getOrDefault(permission, dev.ainer.authorization.domain.GrantPath.BINDING_REQUIRED);
            }

            @Override
            public RelationOutcome relationAllows(Requester.Authenticated subject, PermissionCode permission,
                                                  ResourceRef resource, AuthorizationContext context) {
                return RelationOutcome.DENIED;
            }
        };
    }

    private DomainAuthorizationPolicy relationPolicy(PermissionCode code,
            java.util.function.Predicate<ResourceRef> owns) {
        return new DomainAuthorizationPolicy() {
            @Override
            public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                return dev.ainer.authorization.domain.GrantPath.RELATION_DERIVED;
            }

            @Override
            public RelationOutcome relationAllows(Requester.Authenticated subject, PermissionCode permission,
                                                  ResourceRef resource, AuthorizationContext context) {
                return owns.test(resource) ? RelationOutcome.ALLOWED : RelationOutcome.DENIED;
            }
        };
    }

    private DomainAuthorizationPolicy eitherPolicy(PermissionCode code,
            java.util.function.Predicate<ResourceRef> owns) {
        return new DomainAuthorizationPolicy() {
            @Override
            public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                return dev.ainer.authorization.domain.GrantPath.BINDING_OR_RELATION;
            }

            @Override
            public RelationOutcome relationAllows(Requester.Authenticated subject, PermissionCode permission,
                                                  ResourceRef resource, AuthorizationContext context) {
                return owns.test(resource) ? RelationOutcome.ALLOWED : RelationOutcome.DENIED;
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
