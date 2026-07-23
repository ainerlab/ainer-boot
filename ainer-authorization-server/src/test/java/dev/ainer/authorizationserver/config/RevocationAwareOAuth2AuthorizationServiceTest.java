package dev.ainer.authorizationserver.config;

import dev.ainer.module.identity.account.application.IdentityTokenStatus;
import dev.ainer.module.identity.account.application.IdentityTokenStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RevocationAwareOAuth2AuthorizationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUBJECT_ID = UUID.randomUUID();
    private static final Instant ISSUED_AT = Instant.parse("2026-07-23T02:00:00Z");

    @Test
    void activeUserAuthorizationIsPreserved() {
        OAuth2Authorization authorization = userAuthorization();
        StubAuthorizationService delegate = new StubAuthorizationService(authorization);
        IdentityTokenStatusService statusService = new IdentityTokenStatusService(
                (tenantId, subjectId) -> Optional.of(new IdentityTokenStatus(true, ISSUED_AT.minusSeconds(1))));

        OAuth2Authorization actual = new RevocationAwareOAuth2AuthorizationService(delegate, statusService)
                .findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN);

        assertThat(actual).isSameAs(authorization);
        assertThat(actual.getAccessToken().isInvalidated()).isFalse();
        assertThat(actual.getRefreshToken().isInvalidated()).isFalse();
    }

    @Test
    void revokedUserAuthorizationInvalidatesAccessAndRefreshWithoutPersistingDerivedState() {
        OAuth2Authorization authorization = userAuthorization();
        StubAuthorizationService delegate = new StubAuthorizationService(authorization);
        IdentityTokenStatusService statusService = new IdentityTokenStatusService(
                (tenantId, subjectId) -> Optional.of(new IdentityTokenStatus(true, ISSUED_AT)));

        OAuth2Authorization actual = new RevocationAwareOAuth2AuthorizationService(delegate, statusService)
                .findByToken("refresh-token", OAuth2TokenType.REFRESH_TOKEN);

        assertThat(actual).isNotSameAs(authorization);
        assertThat(actual.getAccessToken().isInvalidated()).isTrue();
        assertThat(actual.getRefreshToken().isInvalidated()).isTrue();
        assertThat(delegate.saveCalls).isZero();
    }

    @Test
    void serviceAuthorizationDoesNotQueryIdentity() {
        OAuth2Authorization authorization = authorization(Map.of("actor_type", "SERVICE"));
        IdentityTokenStatusService statusService = new IdentityTokenStatusService(
                (tenantId, subjectId) -> {
                    throw new AssertionError("Service token must not query Identity");
                });

        OAuth2Authorization actual = new RevocationAwareOAuth2AuthorizationService(
                new StubAuthorizationService(authorization), statusService)
                .findByToken("access-token", OAuth2TokenType.ACCESS_TOKEN);

        assertThat(actual).isSameAs(authorization);
    }

    private OAuth2Authorization userAuthorization() {
        return authorization(Map.of(
                "actor_type", "USER",
                "sub", SUBJECT_ID.toString(),
                "tenant_id", TENANT_ID.toString()));
    }

    private OAuth2Authorization authorization(Map<String, Object> claims) {
        RegisteredClient client = RegisteredClient.withId("registered-client-id")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example.com/callback")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(300),
                Set.of("workspace.write"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "refresh-token", ISSUED_AT, ISSUED_AT.plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName(SUBJECT_ID.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("workspace.write"))
                .token(accessToken, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims))
                .refreshToken(refreshToken)
                .build();
    }

    private static final class StubAuthorizationService implements OAuth2AuthorizationService {

        private final OAuth2Authorization authorization;
        private int saveCalls;

        private StubAuthorizationService(OAuth2Authorization authorization) {
            this.authorization = authorization;
        }

        @Override
        public void save(OAuth2Authorization authorization) {
            saveCalls++;
        }

        @Override
        public void remove(OAuth2Authorization authorization) {
        }

        @Override
        public OAuth2Authorization findById(String id) {
            return authorization;
        }

        @Override
        public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
            return authorization;
        }
    }
}
