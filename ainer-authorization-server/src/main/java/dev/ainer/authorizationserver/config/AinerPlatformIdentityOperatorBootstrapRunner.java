package dev.ainer.authorizationserver.config;

import dev.ainer.security.AinerSecurityScopes;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class AinerPlatformIdentityOperatorBootstrapRunner implements ApplicationRunner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final AinerAuthorizationServerProperties properties;
    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    AinerPlatformIdentityOperatorBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AinerAuthorizationServerProperties.PlatformIdentityOperatorBootstrap bootstrap =
                properties.getPlatformIdentityOperatorBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String clientId = bootstrap.getClientId();
        if (clientId == null || !IDENTIFIER.matcher(clientId).matches()) {
            throw new IllegalStateException(
                    "Ainer platform identity operator bootstrap client id is invalid");
        }
        RegisteredClient existing = repository.findByClientId(clientId);
        if (existing != null) {
            requireExactExistingClient(existing);
            return;
        }
        String secret = bootstrap.getClientSecret();
        if (secret == null || secret.length() < 24 || secret.length() > 128) {
            throw new IllegalStateException(
                    "Ainer platform identity operator bootstrap secret must contain 24 to 128 characters");
        }
        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Ainer platform identity operator")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(1))
                        .build());
        client.scope(AinerSecurityScopes.PLATFORM_TENANTS_READ);
        client.scope(AinerSecurityScopes.PLATFORM_TENANTS_WRITE);
        client.scope(AinerSecurityScopes.PLATFORM_USERS_READ);
        client.scope(AinerSecurityScopes.PLATFORM_USERS_WRITE);
        repository.save(client.build());
    }

    private void requireExactExistingClient(RegisteredClient client) {
        Set<String> expectedScopes = Set.of(
                AinerSecurityScopes.PLATFORM_TENANTS_READ,
                AinerSecurityScopes.PLATFORM_TENANTS_WRITE,
                AinerSecurityScopes.PLATFORM_USERS_READ,
                AinerSecurityScopes.PLATFORM_USERS_WRITE);
        Object tenantId = client.getClientSettings().getSetting(
                AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING);
        Object introspectionAllowed = client.getClientSettings().getSetting(
                AinerAuthorizationServerConfiguration
                        .CLIENT_INTROSPECTION_ALLOWED_SETTING);
        if (!client.getScopes().equals(expectedScopes)
                || !client.getAuthorizationGrantTypes().equals(
                        Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS))
                || !client.getClientAuthenticationMethods().equals(
                        Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC))
                || tenantId != null
                || Boolean.TRUE.equals(introspectionAllowed)
                || !OAuth2TokenFormat.SELF_CONTAINED.equals(
                        client.getTokenSettings().getAccessTokenFormat())
                || !Duration.ofMinutes(1).equals(
                        client.getTokenSettings().getAccessTokenTimeToLive())) {
            throw new IllegalStateException(
                    "Existing Ainer platform identity operator does not match the required policy");
        }
    }
}
