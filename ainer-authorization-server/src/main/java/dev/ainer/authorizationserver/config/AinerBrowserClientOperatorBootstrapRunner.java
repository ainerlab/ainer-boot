package dev.ainer.authorizationserver.config;

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
import java.util.UUID;
import java.util.regex.Pattern;

final class AinerBrowserClientOperatorBootstrapRunner implements ApplicationRunner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final AinerAuthorizationServerProperties properties;
    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    AinerBrowserClientOperatorBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AinerAuthorizationServerProperties.BrowserClientControlOperatorBootstrap bootstrap =
                properties.getBrowserClientControlOperatorBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String clientId = bootstrap.getClientId();
        if (clientId == null || !IDENTIFIER.matcher(clientId).matches()) {
            throw new IllegalStateException(
                    "Ainer browser-client-control operator bootstrap client id is invalid");
        }
        if (repository.findByClientId(clientId) != null) {
            return;
        }
        String secret = bootstrap.getClientSecret();
        if (secret == null || secret.length() < 24 || secret.length() > 128) {
            throw new IllegalStateException(
                    "Ainer browser-client-control operator bootstrap secret must contain 24 to 128 characters");
        }
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Ainer browser-client-control operator")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerAuthorizationServerConfiguration.BROWSER_CLIENT_CONTROL_MANAGE_SCOPE)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(1))
                        .build())
                .build();
        repository.save(client);
    }
}
