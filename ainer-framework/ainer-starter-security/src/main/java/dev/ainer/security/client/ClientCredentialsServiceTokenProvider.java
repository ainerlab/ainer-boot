package dev.ainer.security.client;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.URI;
import java.util.Set;

public final class ClientCredentialsServiceTokenProvider {

    private static final String REGISTRATION_ID = "ainer-service-client";

    private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;
    private final AnonymousAuthenticationToken principal;

    public ClientCredentialsServiceTokenProvider(
            URI tokenUri,
            String clientId,
            String clientSecret,
            Set<String> scopes,
            boolean allowInsecureHttp) {
        validate(tokenUri, clientId, clientSecret, scopes, allowInsecureHttp);
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .tokenUri(tokenUri.toString())
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(scopes)
                .build();
        InMemoryClientRegistrationRepository registrations =
                new InMemoryClientRegistrationRepository(registration);
        manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        manager.setAuthorizedClientProvider(provider);
        principal = new AnonymousAuthenticationToken(
                "ainer-service-client", clientId, AuthorityUtils.createAuthorityList("ROLE_SERVICE"));
    }

    public String accessToken() {
        try {
            OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(REGISTRATION_ID)
                    .principal(principal)
                    .build());
            if (client == null || client.getAccessToken() == null
                    || client.getAccessToken().getTokenValue().isBlank()) {
                throw new ServiceTokenException("Service token endpoint returned no access token");
            }
            return client.getAccessToken().getTokenValue();
        } catch (ServiceTokenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceTokenException("Service token acquisition failed", exception);
        }
    }

    private void validate(
            URI tokenUri,
            String clientId,
            String clientSecret,
            Set<String> scopes,
            boolean allowInsecureHttp) {
        if (tokenUri == null || tokenUri.getHost() == null
                || tokenUri.getUserInfo() != null || tokenUri.getQuery() != null || tokenUri.getFragment() != null) {
            throw new IllegalArgumentException("Service token URI is invalid");
        }
        if (!"https".equalsIgnoreCase(tokenUri.getScheme())
                && !(allowInsecureHttp && "http".equalsIgnoreCase(tokenUri.getScheme()))) {
            throw new IllegalArgumentException("Service token URI must use HTTPS");
        }
        if (clientId == null || clientId.isBlank() || clientId.length() > 128) {
            throw new IllegalArgumentException("Service client id is invalid");
        }
        if (clientSecret == null || clientSecret.length() < 24 || clientSecret.length() > 256) {
            throw new IllegalArgumentException("Service client secret must contain 24 to 256 characters");
        }
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope == null || scope.isBlank())) {
            throw new IllegalArgumentException("Service client scopes are invalid");
        }
    }
}
