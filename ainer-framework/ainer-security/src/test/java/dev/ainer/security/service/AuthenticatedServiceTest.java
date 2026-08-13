package dev.ainer.security.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedServiceTest {

    @Test
    void exposesOnlyValidatedServiceIdentityAndAuthorities() {
        AuthenticatedService service = new AuthenticatedService(
                "ainer-identity-relay", Set.of("SCOPE_identity.accounts.read"));

        assertThat(service.serviceId()).isEqualTo("ainer-identity-relay");
        assertThat(service.hasAuthority("SCOPE_identity.accounts.read")).isTrue();
    }

    @Test
    void missingAuthorityFailsClosed() {
        AuthenticatedService service = new AuthenticatedService("ainer-platform", Set.of());

        assertThatThrownBy(() -> service.requireAuthority("SCOPE_missing"))
                .isInstanceOf(dev.ainer.core.error.BusinessException.class);
    }
}
