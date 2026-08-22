package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.policy.GrantAdministrationPolicy;
import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GrantAdministrationGuardTest {

    @Test
    void missingHostPolicyFailsClosedEvenWithServiceManagementScope() {
        ObjectProvider<GrantAdministrationPolicy> policies =
                new StaticListableBeanFactory().getBeanProvider(GrantAdministrationPolicy.class);
        GrantAdministrationGuard guard = new GrantAdministrationGuard(
                policies,
                new PermissionRegistry(),
                unusedBindingRepository(),
                new StaticListableBeanFactory()
                        .getBeanProvider(AuthorizationDecisionAuditService.class));
        IdentityAuthorityRef authority = new IdentityAuthorityRef("https://auth.ainer.test");
        AuthenticatedPrincipal actor = new AuthenticatedPrincipal(
                new ServiceSubjectRef(authority, "svc-management"),
                authority,
                TokenProfile.SERVICE_V1,
                "1",
                Set.of("ainer-api"),
                Set.of(GrantAdministrationGuard.MANAGE_SCOPE),
                "client_credentials",
                "test-client");

        BusinessException failure = catchThrowableOfType(
                BusinessException.class, () -> guard.requireManager(actor));

        assertThat(failure.errorCode()).isEqualTo(AuthorizationErrorCode.GRANT_ADMINISTRATION_DENIED);
    }

    private static SubjectBindingRepository unusedBindingRepository() {
        return (SubjectBindingRepository) Proxy.newProxyInstance(
                GrantAdministrationGuardTest.class.getClassLoader(),
                new Class<?>[]{SubjectBindingRepository.class},
                (proxy, method, args) -> {
                    throw new AssertionError("deny-all manager check must not query bindings");
                });
    }
}
