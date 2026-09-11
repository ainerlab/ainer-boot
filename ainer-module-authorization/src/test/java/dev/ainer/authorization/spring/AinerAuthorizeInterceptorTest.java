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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AinerAuthorizeInterceptor} 的两类契约：
 * <ol>
 *   <li>CHALLENGE 传输契约：高风险权限缺少近期强认证时，响应必须携带 RFC 9470 的
 *       {@code WWW-Authenticate} 挑战头并抛出 401 业务异常；</li>
 *   <li>端点授权声明契约：{@code @EndpointAccess} 三种口径的放行/拒绝，以及未声明端点在
 *       {@code FAIL_CLOSED}（默认）与 {@code WARN} 下的处置。</li>
 * </ol>
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

    // ---- 端点访问声明与未声明端点处置（FAIL_CLOSED / WARN） ----

    @Test
    void undeclaredHandlerIsDeniedInFailClosedMode() {
        var interceptor = new AinerAuthorizeInterceptor(manager());

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/undeclared"),
                new MockHttpServletResponse(),
                handlerMethod(UndeclaredController.class, "peek")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StandardErrorCode.FORBIDDEN);
    }

    @Test
    void undeclaredHandlerIsAllowedInWarnMode() {
        var interceptor = new AinerAuthorizeInterceptor(
                manager(), EndpointAuthorizationMode.WARN, List.of());

        assertThat(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/undeclared"),
                new MockHttpServletResponse(),
                handlerMethod(UndeclaredController.class, "peek")))
                .isTrue();
    }

    @Test
    void frameworkProvidedHandlerIsNotGated() {
        // 第三方 handler（错误分发/Actuator/springdoc）由外层安全链负责；本包被配置为「框架包」
        var interceptor = new AinerAuthorizeInterceptor(
                manager(), EndpointAuthorizationMode.FAIL_CLOSED,
                List.of("dev.ainer.authorization.spring"));

        assertThat(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/error"),
                new MockHttpServletResponse(),
                handlerMethod(UndeclaredController.class, "peek")))
                .isTrue();
    }

    @Test
    void publicDeclarationAllowsAnonymousRequest() {
        var interceptor = new AinerAuthorizeInterceptor(manager());

        assertThat(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/public-probe"),
                new MockHttpServletResponse(),
                handlerMethod(DeclaredControllers.PublicController.class, "peek")))
                .isTrue();
    }

    @Test
    void authenticatedDeclarationRejectsAnonymousAndAcceptsAuthenticatedSubject() {
        var interceptor = new AinerAuthorizeInterceptor(manager());
        var handler = handlerMethod(DeclaredControllers.AuthenticatedController.class, "peek");
        var request = new MockHttpServletRequest("GET", "/api/authenticated-probe");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StandardErrorCode.UNAUTHENTICATED);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("subject-1", "n/a", List.of()));
        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    @Test
    void classLevelDeclarationCoversAllHandlers() {
        // 类级 DELEGATED：方法上没有注解也算已声明，但仍要求已认证主体
        var interceptor = new AinerAuthorizeInterceptor(manager());
        var handler = handlerMethod(DeclaredControllers.ClassLevelDelegatedController.class, "peek");
        var request = new MockHttpServletRequest("GET", "/api/delegated-probe");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(BusinessException.class);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("subject-1", "n/a", List.of()));
        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    @Test
    void anonymousAuthenticationIsNotEnoughForAuthenticatedDeclaration() {
        var interceptor = new AinerAuthorizeInterceptor(manager());
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/authenticated-probe"),
                new MockHttpServletResponse(),
                handlerMethod(DeclaredControllers.AuthenticatedController.class, "peek")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StandardErrorCode.UNAUTHENTICATED);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static HandlerMethod handlerMethod(Class<?> controllerType, String methodName) {
        try {
            return new HandlerMethod(
                    controllerType.getDeclaredConstructor().newInstance(),
                    controllerType.getDeclaredMethod(methodName));
        } catch (ReflectiveOperationException broken) {
            throw new IllegalStateException(broken);
        }
    }

    /** 测试用未声明 handler：既没有 @AinerAuthorize 也没有 @EndpointAccess。 */
    static class UndeclaredController {

        public String peek() {
            return "ok";
        }
    }

    /** 测试用已声明 handler。 */
    static final class DeclaredControllers {

        private DeclaredControllers() {
        }

        @RestController
        static class PublicController {

            @EndpointAccess(kind = EndpointAccess.Kind.PUBLIC, reason = "单元测试夹具：匿名端点")
            public String peek() {
                return "ok";
            }
        }

        @RestController
        static class AuthenticatedController {

            @EndpointAccess(kind = EndpointAccess.Kind.AUTHENTICATED, reason = "单元测试夹具：仅要求登录")
            public String peek() {
                return "ok";
            }
        }

        @RestController
        @EndpointAccess(kind = EndpointAccess.Kind.DELEGATED, reason = "单元测试夹具：类级委托声明")
        static class ClassLevelDelegatedController {

            public String peek() {
                return "ok";
            }
        }
    }

    /** 测试用受注解 handler。 */
    static class SampleController {

        @AinerAuthorize(permission = "test.transfer")
        public String transfer() {
            return "ok";
        }
    }
}
