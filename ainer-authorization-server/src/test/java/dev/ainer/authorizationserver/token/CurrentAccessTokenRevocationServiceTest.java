package dev.ainer.authorizationserver.token;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentAccessTokenRevocationServiceTest {

    @Test
    void revokesOnlyTheResolvedCurrentAccessToken() {
        StubAuthorizationService authorizations =
                new StubAuthorizationService(activeAuthorization("current-access-token"));

        new CurrentAccessTokenRevocationService(authorizations)
                .revoke("current-access-token");

        assertThat(authorizations.saved).isNotNull();
        assertThat(authorizations.saved.getAccessToken().isInvalidated()).isTrue();
    }

    @Test
    void missingOrInactiveTokenFailsAsUnauthenticatedWithoutWriting() {
        StubAuthorizationService missing = new StubAuthorizationService(null);
        assertUnauthenticated(() ->
                new CurrentAccessTokenRevocationService(missing).revoke("missing-token"));
        assertThat(missing.saved).isNull();

        OAuth2Authorization active = activeAuthorization("revoked-token");
        OAuth2Authorization inactive = OAuth2Authorization.from(active)
                .invalidate(active.getAccessToken().getToken())
                .build();
        StubAuthorizationService revoked = new StubAuthorizationService(inactive);
        assertUnauthenticated(() ->
                new CurrentAccessTokenRevocationService(revoked).revoke("revoked-token"));
        assertThat(revoked.saved).isNull();
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

    private static void assertUnauthenticated(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.UNAUTHENTICATED));
    }

    private static final class StubAuthorizationService implements OAuth2AuthorizationService {

        private final OAuth2Authorization found;
        private OAuth2Authorization saved;

        private StubAuthorizationService(OAuth2Authorization found) {
            this.found = found;
        }

        @Override
        public void save(OAuth2Authorization authorization) {
            saved = authorization;
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
            return found;
        }
    }
}
