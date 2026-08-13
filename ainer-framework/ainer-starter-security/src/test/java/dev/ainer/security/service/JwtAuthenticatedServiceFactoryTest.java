package dev.ainer.security.service;

import dev.ainer.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticatedServiceFactoryTest {

    @Test
    void acceptsOnlyServiceJwt() {
        Jwt jwt = new Jwt(
                "token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "ainer-relay", "actor_type", "SERVICE",
                        "token_profile", "SERVICE_V1", "claim_contract_version", "1"));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(() -> "SCOPE_identity.access-events.publish"));

        AuthenticatedService service = JwtAuthenticatedServiceFactory.from(authentication);

        assertThat(service.serviceId()).isEqualTo("ainer-relay");
        assertThat(service.serviceId()).isEqualTo("ainer-relay");
    }

    @Test
    void rejectsUserAndNonJwtAuthentication() {
        Jwt user = new Jwt(
                "token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "user", "actor_type", "USER"));

        assertThatThrownBy(() -> JwtAuthenticatedServiceFactory.from(new JwtAuthenticationToken(user)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> JwtAuthenticatedServiceFactory.from(
                new TestingAuthenticationToken("service", "secret")))
                .isInstanceOf(BusinessException.class);
    }
}
