package dev.ainer.authorizationserver.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Set;

final class AinerTokenIntrospectionAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;

    AinerTokenIntrospectionAuthenticationProvider(
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService) {
        this(new OAuth2TokenIntrospectionAuthenticationProvider(
                registeredClientRepository, authorizationService));
    }

    AinerTokenIntrospectionAuthenticationProvider(AuthenticationProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2TokenIntrospectionAuthenticationToken request =
                (OAuth2TokenIntrospectionAuthenticationToken) authentication;
        if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated()
                || !isIntrospectionAllowed(client.getRegisteredClient())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenIntrospectionAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean isIntrospectionAllowed(RegisteredClient client) {
        return client != null
                && Boolean.TRUE.equals(client.getClientSettings().getSetting(
                        AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING))
                && client.getScopes().equals(Set.of(
                        AinerAuthorizationServerConfiguration.INTROSPECTION_CLIENT_SCOPE))
                && client.getClientSettings().getSetting(
                        AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING) == null;
    }
}
