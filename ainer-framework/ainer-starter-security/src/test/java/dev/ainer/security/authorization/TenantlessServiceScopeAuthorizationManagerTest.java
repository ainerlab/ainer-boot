package dev.ainer.security.authorization;

import dev.ainer.security.AinerSecurityScopes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantlessServiceScopeAuthorizationManagerTest {

    private final TenantlessServiceScopeAuthorizationManager manager =
            new TenantlessServiceScopeAuthorizationManager(AinerSecurityScopes.PLATFORM_METRICS_READ);
    private final RequestAuthorizationContext context =
            new RequestAuthorizationContext(new MockHttpServletRequest());

    @Test
    void grantsTenantlessServiceWithRequiredScope() {
        assertThat(manager.authorize(
                        () -> jwt("SERVICE", false, AinerSecurityScopes.PLATFORM_METRICS_READ), context)
                .isGranted()).isTrue();
    }

    @Test
    void rejectsUserEvenWithRequiredScope() {
        assertThat(manager.authorize(
                        () -> jwt("USER", false, AinerSecurityScopes.PLATFORM_METRICS_READ), context)
                .isGranted()).isFalse();
    }

    @Test
    void rejectsTenantBoundService() {
        assertThat(manager.authorize(
                        () -> jwt("SERVICE", true, AinerSecurityScopes.PLATFORM_METRICS_READ), context)
                .isGranted()).isFalse();
    }

    @Test
    void rejectsServiceWithoutRequiredScope() {
        assertThat(manager.authorize(() -> jwt("SERVICE", false, "ai.invoke"), context)
                .isGranted()).isFalse();
    }

    @Test
    void rejectsServiceWithoutSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.parse("2026-07-23T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-23T00:01:00Z"))
                .claim("actor_type", "SERVICE")
                .claim("scope", AinerSecurityScopes.PLATFORM_METRICS_READ)
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(
                        "SCOPE_" + AinerSecurityScopes.PLATFORM_METRICS_READ)));

        assertThat(manager.authorize(() -> authentication, context).isGranted()).isFalse();
    }

    @Test
    void rejectsNonJwtAuthentication() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "service", "secret", List.of(new SimpleGrantedAuthority(
                        "SCOPE_" + AinerSecurityScopes.PLATFORM_METRICS_READ)));

        assertThat(manager.authorize(() -> authentication, context).isGranted()).isFalse();
    }

    private JwtAuthenticationToken jwt(String actorType, boolean tenantBound, String scope) {
        Jwt.Builder jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("metrics-client")
                .issuedAt(Instant.parse("2026-07-23T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-23T00:01:00Z"))
                .claim("actor_type", actorType)
                .claim("scope", scope);
        if (tenantBound) {
            jwt.claim("tenant_id", "tenant:forbidden");
        }
        return new JwtAuthenticationToken(
                jwt.build(),
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
