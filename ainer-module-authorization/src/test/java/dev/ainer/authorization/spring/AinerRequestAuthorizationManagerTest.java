package dev.ainer.authorization.spring;

import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link AinerRequestAuthorizationManager}. Uses hand-written fakes (no Mockito — the
 * repo does not use it) and Spring's {@link MockHttpServletRequest} to verify the
 * decision → AuthorizationResult mapping.
 */
class AinerRequestAuthorizationManagerTest {

    private static final PermissionCode READ = new PermissionCode("test.read");
    private static final ResourceType RESOURCE = new ResourceType("request");
    private static final String TEST_VERSION = "test-adapter";

    private final AuthenticatedPrincipal servicePrincipal = new AuthenticatedPrincipal(
            new ServiceSubjectRef(new IdentityAuthorityRef("https://auth.ainer.test"), "svc-test"),
            new IdentityAuthorityRef("https://auth.ainer.test"),
            TokenProfile.SERVICE_V1,
            "1",
            Set.of("ainer-api"),
            Set.of("test-scope"),
            "client_credentials",
            "test-client");

    @Test
    void noPermissionAttributeReturnsNullToFallThrough() {
        var manager = manager(allowAllService(), resolver(servicePrincipal));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        // 不设 permission attribute
        var result = manager.authorize(auth(), context(request));
        assertThat(result).isNull();
    }

    @Test
    void allowDecisionMapsToGrantedResult() {
        var manager = manager(allowAllService(), resolver(servicePrincipal));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isTrue();
        assertThat(result.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
    }

    @Test
    void denyDecisionMapsToNotGrantedResult() {
        var manager = manager(denyAllService(), resolver(servicePrincipal));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isFalse();
        assertThat(result.outcome()).isEqualTo(AuthorizationOutcome.DENY);
    }

    @Test
    void unauthenticatedPrincipalMapsToNotGrantedResult() {
        var manager = manager(allowAllService(), throwingResolver());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isFalse();
        assertThat(result.outcome()).isEqualTo(AuthorizationOutcome.DENY);
        assertThat(result.reasonCode()).isEqualTo("AUTHENTICATED_REQUIRED");
    }

    @Test
    void publicProjectionAllowWithObligationIsNotGrantedByAdapter() {
        // PUBLIC_PROJECTION 的 ALLOW 带 PublicProjection obligation；adapter 首版不执行 obligation，
        // 因此 isGranted()=false（§8.6：ALLOW+obligations 必须由 DecisionObligationExecutor 消费）。
        // 这是设计预期，直到 obligation executor 实现。
        var manager = manager(publicAllowService(), resolver(servicePrincipal));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");
        request.setAttribute(AinerAuthorizeInterceptor.ACCESS_MODE_ATTRIBUTE, AccessMode.PUBLIC_PROJECTION);

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(result.isGranted()).as("ALLOW with obligations is not grantable by adapter alone (§8.6)")
                .isFalse();
    }

    @Test
    void publicProjectionUsesAnonymousRequesterWhenNoPrincipalExists() {
        var manager = manager(publicAllowService(), throwingResolver());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public-test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");
        request.setAttribute(AinerAuthorizeInterceptor.ACCESS_MODE_ATTRIBUTE, AccessMode.PUBLIC_PROJECTION);

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.outcome()).isEqualTo(AuthorizationOutcome.ALLOW);
        assertThat(result.reasonCode()).isEqualTo("PUBLIC_ALLOWED");
        assertThat(result.isGranted()).as("public projection obligation is still unhandled").isFalse();
    }

    @Test
    void publicProjectionDoesNotDowngradeUnexpectedResolverFailureToAnonymous() {
        var manager = manager(publicAllowService(), () -> {
            throw new IllegalStateException("resolver unavailable");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public-test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");
        request.setAttribute(AinerAuthorizeInterceptor.ACCESS_MODE_ATTRIBUTE, AccessMode.PUBLIC_PROJECTION);

        assertThatThrownBy(() -> manager.authorize(auth(), context(request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("resolver unavailable");
    }

    // ---- fixtures ----

    private AuthorizationService allowAllService() {
        return new AuthorizationService(
                registry(),
                scopeCeiling(),
                publicPolicy(),
                allowPolicy(),
                subject -> Set.of(),
                TEST_VERSION);
    }

    private AuthorizationService denyAllService() {
        return new AuthorizationService(
                registry(),
                (scope, permission) -> false,
                (permission, resource) -> Optional.empty(),
                denyPolicy(),
                subject -> Set.of(),
                TEST_VERSION);
    }

    private AuthorizationService publicAllowService() {
        return new AuthorizationService(
                registry(),
                scopeCeiling(),
                (permission, resource) -> Optional.of(
                        new dev.ainer.authorization.domain.PublicProjection("public")),
                allowPolicy(),
                subject -> Set.of(),
                TEST_VERSION);
    }

    private PermissionRegistry registry() {
        return new PermissionRegistry().register(() -> Set.of(
                new Permission(READ, "read", RESOURCE, RiskTier.LOW, AuditLevel.NONE, false, false)));
    }

    private ScopePermissionCeiling scopeCeiling() {
        return (scope, permission) -> "test-scope".equals(scope);
    }

    private PublicAccessPolicy publicPolicy() {
        return (permission, resource) -> Optional.empty();
    }

    private DomainAuthorizationPolicy allowPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                return dev.ainer.authorization.domain.GrantPath.BINDING_OR_RELATION;
            }

            @Override
            public boolean relationGrants(
                    dev.ainer.authorization.domain.Requester.Authenticated subject,
                    PermissionCode permission,
                    dev.ainer.authorization.domain.ResourceRef resource,
                    dev.ainer.authorization.domain.AuthorizationContext context) {
                return true;
            }

            @Override
            public boolean resourceStateSatisfies(
                    dev.ainer.authorization.domain.Requester.Authenticated subject,
                    PermissionCode permission,
                    dev.ainer.authorization.domain.ResourceRef resource,
                    dev.ainer.authorization.domain.AuthorizationContext context) {
                return true;
            }
        };
    }

    private DomainAuthorizationPolicy denyPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                return null;
            }

            @Override
            public boolean relationGrants(
                    dev.ainer.authorization.domain.Requester.Authenticated s, PermissionCode p,
                    dev.ainer.authorization.domain.ResourceRef r,
                    dev.ainer.authorization.domain.AuthorizationContext c) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(
                    dev.ainer.authorization.domain.Requester.Authenticated s, PermissionCode p,
                    dev.ainer.authorization.domain.ResourceRef r,
                    dev.ainer.authorization.domain.AuthorizationContext c) {
                return false;
            }
        };
    }

    private AuthenticatedPrincipalResolver resolver(AuthenticatedPrincipal principal) {
        return () -> principal;
    }

    private AuthenticatedPrincipalResolver throwingResolver() {
        return () -> {
            throw new dev.ainer.core.error.BusinessException(dev.ainer.core.error.StandardErrorCode.UNAUTHENTICATED);
        };
    }

    private AinerRequestAuthorizationManager manager(
            AuthorizationService service, AuthenticatedPrincipalResolver resolver) {
        return new AinerRequestAuthorizationManager(service, resolver);
    }

    private Supplier<org.springframework.security.core.Authentication> auth() {
        return () -> null; // adapter 不直接用 authentication（通过 resolver 读 SecurityContext）
    }

    private RequestAuthorizationContext context(MockHttpServletRequest request) {
        return new RequestAuthorizationContext(request);
    }
}
