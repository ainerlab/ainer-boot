package dev.ainer.security.service;

import dev.ainer.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedServiceTest {

    @Test
    void exposesOnlyValidatedServiceIdentityAndAuthorities() {
        AuthenticatedService service = new AuthenticatedService(
                "ainer-identity-relay", "tenant:one", Set.of("SCOPE_identity.access-events.publish"));

        assertThat(service.serviceId()).isEqualTo("ainer-identity-relay");
        assertThat(service.requireTenantId()).isEqualTo("tenant:one");
        assertThat(service.hasAuthority("SCOPE_identity.access-events.publish")).isTrue();
    }

    @Test
    void missingTenantAndAuthorityFailClosed() {
        AuthenticatedService service = new AuthenticatedService("ainer-platform", null, Set.of());

        assertThatThrownBy(service::requireTenantId).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireAuthority("SCOPE_missing"))
                .isInstanceOf(BusinessException.class);
    }
}
