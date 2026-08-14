package dev.ainer.authorizationserver.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

final class AinerAdminBrowserClientBootstrapRunner implements ApplicationRunner {

    static final String CLIENT_ID = "ainer-admin-dev";
    static final String CALLBACK_PATH = "/ainer-admin/auth/callback";
    static final String LOGGED_OUT_PATH = "/ainer-admin/auth/logged-out";
    static final String WORKSPACE_READ = "workspace.read";
    static final String WORKSPACE_WRITE = "workspace.write";
    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);
    static final Set<String> SCOPES = Set.of(
            OidcScopes.OPENID,
            OidcScopes.PROFILE,
            WORKSPACE_READ,
            WORKSPACE_WRITE);

    private final AinerAdminBrowserClientProperties properties;
    private final RegisteredClientRepository repository;

    AinerAdminBrowserClientBootstrapRunner(
            AinerAdminBrowserClientProperties properties,
            RegisteredClientRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        URI redirectUri = requireEndpoint(properties.getRedirectUri(), CALLBACK_PATH, "redirect URI");
        URI postLogoutRedirectUri = requireEndpoint(
                properties.getPostLogoutRedirectUri(), LOGGED_OUT_PATH, "post logout redirect URI");
        if (!sameOrigin(redirectUri, postLogoutRedirectUri)) {
            throw new IllegalStateException(
                    "Ainer Admin redirect URI and post logout redirect URI must use the same origin");
        }

        RegisteredClient existing = repository.findByClientId(CLIENT_ID);
        if (existing != null) {
            requireCompatible(existing, redirectUri, postLogoutRedirectUri);
            return;
        }
        repository.save(RegisteredClient.withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(CLIENT_ID)
                .clientName("Ainer Admin development browser client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri.toString())
                .postLogoutRedirectUri(postLogoutRedirectUri.toString())
                .scopes(scopes -> scopes.addAll(SCOPES))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .build())
                .build());
    }

    private static URI requireEndpoint(String value, String requiredPath, String name) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Ainer Admin " + name + " is invalid", exception);
        }
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost());
        if (!uri.isAbsolute()
                || (!secure && !loopbackHttp)
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !requiredPath.equals(uri.getPath())) {
            throw new IllegalStateException(
                    "Ainer Admin " + name + " must be an HTTPS URI (or loopback HTTP) with path "
                            + requiredPath);
        }
        return uri;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equalsIgnoreCase(host)
                || "::1".equals(host);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static void requireCompatible(
            RegisteredClient client,
            URI redirectUri,
            URI postLogoutRedirectUri) {
        boolean compatible = client.getClientSecret() == null
                && client.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))
                && client.getAuthorizationGrantTypes().equals(Set.of(AuthorizationGrantType.AUTHORIZATION_CODE))
                && client.getRedirectUris().equals(Set.of(redirectUri.toString()))
                && client.getPostLogoutRedirectUris().equals(Set.of(postLogoutRedirectUri.toString()))
                && client.getScopes().equals(SCOPES)
                && client.getClientSettings().isRequireProofKey()
                && !client.getClientSettings().isRequireAuthorizationConsent()
                && OAuth2TokenFormat.SELF_CONTAINED.equals(
                        client.getTokenSettings().getAccessTokenFormat())
                && ACCESS_TOKEN_TTL.equals(client.getTokenSettings().getAccessTokenTimeToLive());
        if (!compatible) {
            throw new IllegalStateException(
                    "Existing Ainer Admin browser client does not match the required public PKCE policy");
        }
    }
}
