package dev.ainer.authorizationserver.config;

import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AinerAdminAccessTokenActiveFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAuthorizationContinuesTheRequest() throws Exception {
        StubAuthorizationService authorizations =
                new StubAuthorizationService(activeAuthorization("active-token"));
        MockFilterChain chain = new MockFilterChain();

        execute(authorizations, "active-token", chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(authorizations.lookups).isEqualTo(1);
    }

    @Test
    void inactiveAuthorizationReturnsGeneric401AndClearsAuthentication() throws Exception {
        OAuth2Authorization active = activeAuthorization("inactive-token");
        OAuth2Authorization inactive = OAuth2Authorization.from(active)
                .invalidate(active.getAccessToken().getToken())
                .build();

        MockHttpServletResponse response =
                execute(new StubAuthorizationService(inactive), "inactive-token", new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("AINER.COMMON.UNAUTHENTICATED")
                .doesNotContain("inactive-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lookupFailureReturns503WithoutFallingBackToJwt() throws Exception {
        StubAuthorizationService authorizations = new StubAuthorizationService(null);
        authorizations.failure = new IllegalStateException("database unavailable");

        MockHttpServletResponse response =
                execute(authorizations, "active-token", new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains("AINER.SECURITY.ONLINE_VALIDATION_UNAVAILABLE")
                .doesNotContain("database unavailable", "active-token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingBearerTokenReturns401EvenWhenAnAuthenticationExists() throws Exception {
        StubAuthorizationService authorizations =
                new StubAuthorizationService(activeAuthorization("active-token"));

        MockHttpServletResponse response =
                execute(authorizations, null, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(authorizations.lookups).isZero();
    }

    private static MockHttpServletResponse execute(
            StubAuthorizationService authorizations,
            String tokenValue,
            MockFilterChain chain) throws ServletException, IOException {
        AinerAdminAccessTokenActiveFilter filter = new AinerAdminAccessTokenActiveFilter(
                new DefaultBearerTokenResolver(),
                authorizations,
                new AinerSecurityFailureWriter(new JsonMapper()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "subject-id", "not-used", List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/t/members");
        if (tokenValue != null) {
            request.addHeader("Authorization", "Bearer " + tokenValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static OAuth2Authorization activeAuthorization(String tokenValue) {
        Instant now = Instant.now();
        RegisteredClient client = RegisteredClient.withId("registered-client-id")
                .clientId("ainer-admin-dev")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:5173/ainer-admin/auth/callback")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                now.minusSeconds(5),
                now.plusSeconds(300),
                Set.of("openid", "profile"));
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName("subject-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile"))
                .accessToken(accessToken)
                .build();
    }

    private static final class StubAuthorizationService implements OAuth2AuthorizationService {

        private final OAuth2Authorization found;
        private RuntimeException failure;
        private int lookups;

        private StubAuthorizationService(OAuth2Authorization found) {
            this.found = found;
        }

        @Override
        public void save(OAuth2Authorization authorization) {
        }

        @Override
        public void remove(OAuth2Authorization authorization) {
        }

        @Override
        public OAuth2Authorization findById(String id) {
            return found;
        }

        @Override
        public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
            lookups++;
            if (failure != null) {
                throw failure;
            }
            return found;
        }
    }
}
