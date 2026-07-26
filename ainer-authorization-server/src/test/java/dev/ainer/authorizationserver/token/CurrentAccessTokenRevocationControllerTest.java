package dev.ainer.authorizationserver.token;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentAccessTokenRevocationControllerTest {

    @Test
    void userRevokesTheBearerTokenFromTheAuthenticatedJwt() {
        RecordingRevocationService revocations = new RecordingRevocationService();
        CurrentAccessTokenRevocationController controller =
                new CurrentAccessTokenRevocationController(revocations);

        ApiResponse<CurrentAccessTokenRevocationController.CurrentAccessTokenRevocationResponse> response =
                controller.revoke(authentication("USER"), new MockHttpServletRequest());

        assertThat(revocations.tokenValue).isEqualTo("current-access-token");
        assertThat(response.data().revoked()).isTrue();
    }

    @Test
    void serviceActorCannotUseTheUserSelfRevocationEndpoint() {
        RecordingRevocationService revocations = new RecordingRevocationService();
        CurrentAccessTokenRevocationController controller =
                new CurrentAccessTokenRevocationController(revocations);

        assertThatThrownBy(() ->
                controller.revoke(authentication("SERVICE"), new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.FORBIDDEN));
        assertThat(revocations.tokenValue).isNull();
    }

    private static JwtAuthenticationToken authentication(String actorType) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "current-access-token",
                now.minusSeconds(5),
                now.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "subject-id", "actor_type", actorType));
        return new JwtAuthenticationToken(
                jwt,
                java.util.List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                jwt.getSubject());
    }

    private static final class RecordingRevocationService extends CurrentAccessTokenRevocationService {

        private String tokenValue;

        private RecordingRevocationService() {
            super(new NoopAuthorizationService());
        }

        @Override
        public void revoke(String tokenValue) {
            this.tokenValue = tokenValue;
        }
    }

    private static final class NoopAuthorizationService implements OAuth2AuthorizationService {

        @Override
        public void save(OAuth2Authorization authorization) {
        }

        @Override
        public void remove(OAuth2Authorization authorization) {
        }

        @Override
        public OAuth2Authorization findById(String id) {
            return null;
        }

        @Override
        public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
            return null;
        }
    }
}
