package dev.ainer.authorizationserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerTokenIntrospectionAuthenticationProviderTest {

    @Test
    void ordinaryAuthenticatedClientIsRejectedBeforeOfficialProvider() {
        AtomicInteger delegateCalls = new AtomicInteger();
        AinerTokenIntrospectionAuthenticationProvider provider = new AinerTokenIntrospectionAuthenticationProvider(
                trackingDelegate(delegateCalls));
        OAuth2TokenIntrospectionAuthenticationToken request = request(client(false));

        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT));
        assertThat(delegateCalls).hasValue(0);
    }

    @Test
    void explicitlyTrustedClientDelegatesToOfficialProvider() {
        AtomicInteger delegateCalls = new AtomicInteger();
        AinerTokenIntrospectionAuthenticationProvider provider = new AinerTokenIntrospectionAuthenticationProvider(
                trackingDelegate(delegateCalls));
        OAuth2TokenIntrospectionAuthenticationToken request = request(client(true));

        assertThat(provider.authenticate(request)).isSameAs(request);
        assertThat(delegateCalls).hasValue(1);
        assertThat(provider.supports(request.getClass())).isTrue();
    }

    @Test
    void trustedClientCannotCarryBusinessScopes() {
        AtomicInteger delegateCalls = new AtomicInteger();
        AinerTokenIntrospectionAuthenticationProvider provider = new AinerTokenIntrospectionAuthenticationProvider(
                trackingDelegate(delegateCalls));

        assertInvalidClient(provider, request(client(true, true)));
        assertThat(delegateCalls).hasValue(0);
    }

    private void assertInvalidClient(
            AinerTokenIntrospectionAuthenticationProvider provider,
            OAuth2TokenIntrospectionAuthenticationToken request) {
        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT));
    }

    private AuthenticationProvider trackingDelegate(AtomicInteger calls) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                calls.incrementAndGet();
                return authentication;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return true;
            }
        };
    }

    private OAuth2TokenIntrospectionAuthenticationToken request(RegisteredClient client) {
        OAuth2ClientAuthenticationToken principal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "credentials");
        return new OAuth2TokenIntrospectionAuthenticationToken(
                "token", principal, null, Map.of());
    }

    private RegisteredClient client(boolean introspectionAllowed) {
        return client(introspectionAllowed, false);
    }

    private RegisteredClient client(
            boolean introspectionAllowed,
            boolean businessScope) {
        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("introspection-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerAuthorizationServerConfiguration.INTROSPECTION_CLIENT_SCOPE);
        if (businessScope) {
            client.scope("workspace.read");
        }
        ClientSettings.Builder settings = ClientSettings.builder()
                        .setting(
                                AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING,
                                introspectionAllowed);
        return client.clientSettings(settings.build()).build();
    }
}
