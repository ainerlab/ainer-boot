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
import java.util.UUID;
import java.util.regex.Pattern;

final class AinerMetricsClientBootstrapRunner implements ApplicationRunner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final AinerAuthorizationServerProperties properties;
    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    AinerMetricsClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AinerAuthorizationServerProperties.MetricsClientBootstrap bootstrap =
                properties.getMetricsClientBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String clientId = bootstrap.getClientId();
        if (clientId == null || !IDENTIFIER.matcher(clientId).matches()) {
            throw new IllegalStateException("Ainer bootstrap metrics client id is invalid");
        }
        if (repository.findByClientId(clientId) != null) {
            return;
        }
        String secret = bootstrap.getClientSecret();
        if (secret == null || secret.length() < 24 || secret.length() > 128) {
            throw new IllegalStateException(
                    "Ainer bootstrap metrics client secret must contain 24 to 128 characters");
        }

        repository.save(RegisteredClient.withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Ainer dedicated metrics client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerSecurityScopes.PLATFORM_METRICS_READ)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(1))
                        .build())
                .build());
    }
}
