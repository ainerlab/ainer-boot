package dev.ainer.authorization.spring;

import dev.ainer.authorization.AuthorizationService;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AinerAuthorizeInterceptor} 的 CHALLENGE 传输契约：高风险权限缺少近期强认证时，
 * 响应必须携带 RFC 9470 的 {@code WWW-Authenticate} 挑战头并抛出 401 业务异常。
 */
class AinerAuthorizeInterceptorTest {

    private static final PermissionCode HIGH_RISK = new PermissionCode("test.transfer");
    private static final ResourceType RESOURCE = new ResourceType("request");

    private final HandlerMethod handlerMethod = initHandlerMethod();

    private static HandlerMethod initHandlerMethod() {
        try {
            return new HandlerMethod(new SampleController(),
                    SampleController.class.getDeclaredMethod("transfer"));
        } catch (NoSuchMethodException broken) {
            throw new IllegalStateException(broken);
        }
    }

    @Test
    void challengeDecisionSetsRfc9470HeaderAndThrowsUnauthenticated() {
        var interceptor = new AinerAuthorizeInterceptor(manager());
        var request = new MockHttpServletRequest("POST", "/api/test/transfer");
        var response = new MockHttpServletResponse();
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.transfer");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StandardErrorCode.UNAUTHENTICATED);

        assertThat(response.getHeader("WWW-Authenticate"))
                .isEqualTo(AinerAuthorizeInterceptor.WWW_AUTHENTICATE_CHALLENGE);
        assertThat(AinerAuthorizeInterceptor.WWW_AUTHENTICATE_CHALLENGE)
                .contains("insufficient_user_authentication");
    }

    @Test
    void plainDenyDoesNotSetChallengeHeader() {
        // 普通 DENY 走 403，不得携带 step-up 挑战头（语义混淆会让客户端误判需要重新认证）。
        var manager = new AinerRequestAuthorizationManager(
                denyService(), () -> {
                    throw new dev.ainer.core.error.BusinessException(
                            StandardErrorCode.UNAUTHENTICATED);
                },
                emptyAuditProvider(), null);
        var interceptor = new AinerAuthorizeInterceptor(manager);
        var request = new MockHttpServletRequest("POST", "/api/test/transfer");
        var response = new MockHttpServletResponse();
        request.setAttribute(AinerAuthorizeInterceptor.PERMISSION_ATTRIBUTE, "test.transfer");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BusinessException.class);

        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    // ---- fixtures ----

    /** 高风险权限 + Assurance.NONE：决策引擎返回 CHALLENGE。 */
    private AinerRequestAuthorizationManager manager() {
        var service = new AuthorizationService(
                new PermissionRegistry().register(() -> Set.of(
                        new Permission(HIGH_RISK, "transfer", RESOURCE,
                                RiskTier.HIGH, AuditLevel.ON_DECISION, false, false))),
                (scope, permission) -> true,
                (PublicAccessPolicy) (permission, resource) -> Optional.empty(),
                new DomainAuthorizationPolicy() {
                    @Override
                    public GrantPath pathFor(PermissionCode permission) {
                        return GrantPath.BINDING_OR_RELATION;
                    }

                    @Override
                    public boolean relationGrants(
                            dev.ainer.authorization.domain.Requester.Authenticated s,
                            PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return true;
                    }

                    @Override
                    public boolean resourceStateSatisfies(
                            dev.ainer.authorization.domain.Requester.Authenticated s,
                            PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return true;
                    }
                },
                subject -> Set.of(),
                "test-interceptor");
        return new AinerRequestAuthorizationManager(service, this::servicePrincipal,
                emptyAuditProvider(), null);
    }

    private dev.ainer.security.token.AuthenticatedPrincipal servicePrincipal() {
        return new AuthenticatedPrincipal(
                new ServiceSubjectRef(new IdentityAuthorityRef("https://auth.ainer.test"), "svc-test"),
                new IdentityAuthorityRef("https://auth.ainer.test"),
                TokenProfile.SERVICE_V1,
                "1",
                Set.of("ainer-api"),
                Set.of("test-scope"),
                "client_credentials",
                "test-client");
    }

    private AuthorizationService denyService() {
        return new AuthorizationService(
                new PermissionRegistry().register(() -> Set.of(
                        new Permission(HIGH_RISK, "transfer", RESOURCE,
                                RiskTier.HIGH, AuditLevel.ON_DECISION, false, false))),
                (scope, permission) -> false,
                (PublicAccessPolicy) (permission, resource) -> Optional.empty(),
                new DomainAuthorizationPolicy() {
                    @Override
                    public GrantPath pathFor(PermissionCode permission) {
                        return null;
                    }

                    @Override
                    public boolean relationGrants(
                            dev.ainer.authorization.domain.Requester.Authenticated s,
                            PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return false;
                    }

                    @Override
                    public boolean resourceStateSatisfies(
                            dev.ainer.authorization.domain.Requester.Authenticated s,
                            PermissionCode p,
                            dev.ainer.authorization.domain.ResourceRef r,
                            dev.ainer.authorization.domain.AuthorizationContext c) {
                        return false;
                    }
                },
                subject -> Set.of(),
                "test-interceptor");
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

    /** 测试用受注解 handler。 */
    static class SampleController {

        @AinerAuthorize(permission = "test.transfer")
        public String transfer() {
            return "ok";
        }
    }
}
