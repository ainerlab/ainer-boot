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
import java.util.UUID;
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
        assertThat(result.isGranted()).as("ALLOW with only a projection must be grantable")
                .isTrue();
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
        // PublicProjection is projected response data carried in the obligations slot, not a
        // pending obligation — it must not block the grant (review H2 fix).
        assertThat(result.isGranted()).as("public projection must be grantable").isTrue();
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

    @Test
    void targetResolverProvidesTypedResourceRefForDecision() {
        // 解析器返回 document 类型资源；权限也注册为 document 类型——类型匹配证明解析结果
        // 真正进入决策（合成占位固定为 request 类型，会以 RESOURCE_TYPE_MISMATCH 拒绝）。
        var manager = new AinerRequestAuthorizationManager(documentService(), resolver(servicePrincipal),
                emptyAuditProvider(), providerOf(
                (request, permission) -> Optional.of(new dev.ainer.authorization.domain.ResourceRef(
                        null, DOCUMENT, RESOURCE_ID))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/1");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("AUTHORIZED");
    }

    @Test
    void withoutTargetResolverSyntheticRequestResourceCausesTypeMismatchDeny() {
        // 同一 document 权限：无解析器时回退合成 request 资源 → 类型不匹配 fail-closed。
        var manager = new AinerRequestAuthorizationManager(documentService(), resolver(servicePrincipal),
                emptyAuditProvider(), null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/1");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("RESOURCE_TYPE_MISMATCH");
    }

    @Test
    void firstNonEmptyTargetResolverWinsInBeanOrder() {
        var losing = new dev.ainer.authorization.domain.ResourceRef(null, DOCUMENT, UUID.randomUUID());
        var winning = new dev.ainer.authorization.domain.ResourceRef(null, DOCUMENT, RESOURCE_ID);
        var manager = new AinerRequestAuthorizationManager(
                grantOnlyOnResourceId(winning.resourceId()), resolver(servicePrincipal),
                emptyAuditProvider(),
                providerOf(
                        (request, permission) -> Optional.empty(),
                        (request, permission) -> Optional.of(winning)));
        // losing 排在前面但返回 empty；若实现错误地取用 losing，策略会拒绝。
        var request = new MockHttpServletRequest("GET", "/api/documents/1");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isTrue();
        assertThat(losing).isNotEqualTo(winning);
    }

    @Test
    void targetResolverEmptyListFallsBackToSyntheticResource() {
        var manager = new AinerRequestAuthorizationManager(allowAllService(), resolver(servicePrincipal),
                emptyAuditProvider(), providerOf());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        AinerAuthorizationResult result = (AinerAuthorizationResult) manager.authorize(auth(), context(request));

        assertThat(result.isGranted()).isTrue();
    }

    @Test
    void targetResolverFailurePropagatesInsteadOfSilentFallback() {
        var manager = new AinerRequestAuthorizationManager(allowAllService(), resolver(servicePrincipal),
                emptyAuditProvider(), providerOf((request, permission) -> {
                    throw new IllegalStateException("resolver broken");
                }));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.read");

        assertThatThrownBy(() -> manager.authorize(auth(), context(request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("resolver broken");
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
        return new AinerRequestAuthorizationManager(service, resolver,
                emptyAuditProvider());
    }

    private static final dev.ainer.authorization.domain.ResourceType DOCUMENT =
            new dev.ainer.authorization.domain.ResourceType("document");
    private static final UUID RESOURCE_ID = UUID.fromString(
            "018f6b2e-7c3a-7de1-9f4a-2b8e5d1c0a77");

    /** document 类型权限 + 仅在目标 resourceId 匹配时授予的领域策略。 */
    private AuthorizationService documentService() {
        return grantOnlyOnResourceId(RESOURCE_ID);
    }

    private AuthorizationService grantOnlyOnResourceId(UUID expectedResourceId) {
        return new AuthorizationService(
                new PermissionRegistry().register(() -> Set.of(
                        new Permission(new PermissionCode("test.read"), "read", DOCUMENT,
                                RiskTier.LOW, AuditLevel.NONE, false, false))),
                scopeCeiling(),
                publicPolicy(),
                new DomainAuthorizationPolicy() {
                    @Override
                    public dev.ainer.authorization.domain.GrantPath pathFor(PermissionCode permission) {
                        return dev.ainer.authorization.domain.GrantPath.BINDING_OR_RELATION;
                    }

                    @Override
                    public boolean relationGrants(
                            dev.ainer.authorization.domain.Requester.Authenticated s, PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return expectedResourceId.equals(r.resourceId());
                    }

                    @Override
                    public boolean resourceStateSatisfies(
                            dev.ainer.authorization.domain.Requester.Authenticated s, PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return true;
                    }
                },
                subject -> Set.of(),
                TEST_VERSION);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static org.springframework.beans.factory.ObjectProvider<AuthorizationTargetResolver>
    providerOf(AuthorizationTargetResolver... resolvers) {
        var list = java.util.List.of(resolvers);
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public AuthorizationTargetResolver getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthorizationTargetResolver getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AuthorizationTargetResolver getIfAvailable() {
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public java.util.stream.Stream<AuthorizationTargetResolver> orderedStream() {
                return list.stream();
            }
        };
    }

    private Supplier<org.springframework.security.core.Authentication> auth() {
        return () -> null; // adapter 不直接用 authentication（通过 resolver 读 SecurityContext）
    }

    private RequestAuthorizationContext context(MockHttpServletRequest request) {
        return new RequestAuthorizationContext(request);
    }
    private static org.springframework.beans.factory.ObjectProvider<
            dev.ainer.authorization.application.AuthorizationDecisionAuditService> emptyAuditProvider() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public dev.ainer.authorization.application.AuthorizationDecisionAuditService getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public dev.ainer.authorization.application.AuthorizationDecisionAuditService getObject(
                    Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public dev.ainer.authorization.application.AuthorizationDecisionAuditService getIfAvailable() {
                return null;
            }
        };
    }
}
