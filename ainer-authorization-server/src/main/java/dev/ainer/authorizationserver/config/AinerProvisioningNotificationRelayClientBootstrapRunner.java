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

final class AinerProvisioningNotificationRelayClientBootstrapRunner
        implements ApplicationRunner {

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final AinerAuthorizationServerProperties properties;
    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    AinerProvisioningNotificationRelayClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AinerAuthorizationServerProperties.ProvisioningNotificationRelayClientBootstrap
                bootstrap = properties.getProvisioningNotificationRelayClientBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String clientId = bootstrap.getClientId();
        if (clientId == null || !IDENTIFIER.matcher(clientId).matches()) {
            throw new IllegalStateException(
                    "Ainer provisioning notification relay bootstrap client id is invalid");
        }
        RegisteredClient existing = repository.findByClientId(clientId);
        if (existing != null) {
            requireExactExistingClient(existing);
            return;
        }
        String secret = bootstrap.getClientSecret();
        if (secret == null || secret.length() < 24 || secret.length() > 128) {
            throw new IllegalStateException(
                    "Ainer provisioning notification relay bootstrap secret "
                            + "must contain 24 to 128 characters");
        }
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Ainer provisioning notification relay")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerSecurityScopes.IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(1))
                        .build())
                .build();
        repository.save(client);
    }

    private void requireExactExistingClient(RegisteredClient client) {
        Object tenantId = client.getClientSettings().getSetting(
                AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING);
        Object introspectionAllowed = client.getClientSettings().getSetting(
                AinerAuthorizationServerConfiguration
                        .CLIENT_INTROSPECTION_ALLOWED_SETTING);
        if (!client.getScopes().equals(Set.of(
                        AinerSecurityScopes
                                .IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH))
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
                    "Existing Ainer provisioning notification relay client "
                            + "does not match the required policy");
        }
    }
}
