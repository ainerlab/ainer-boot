package dev.ainer.authorizationserver.config;

import dev.ainer.module.identity.foundation.ServicePrincipal;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.security.principal.IdentityAuthorityRef;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class AinerMachineClientBootstrapRunner implements ApplicationRunner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final AinerAuthorizationServerProperties properties;
    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ServicePrincipalFoundationService servicePrincipalFoundationService;

    AinerMachineClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder,
            ServicePrincipalFoundationService servicePrincipalFoundationService) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.servicePrincipalFoundationService = servicePrincipalFoundationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AinerAuthorizationServerProperties.MachineClientBootstrap bootstrap =
                properties.getMachineClientBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }

        String clientId = requireIdentifier(bootstrap.getClientId(), "client id");
        if (repository.findByClientId(clientId) != null) {
            return;
        }
        String secret = bootstrap.getClientSecret();
        if (secret == null || secret.length() < 24 || secret.length() > 128) {
            throw new IllegalStateException("Ainer bootstrap machine client secret must contain 24 to 128 characters");
        }
        Set<String> scopes = new HashSet<>(bootstrap.getScopes());
        if (scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope == null || !SCOPE.matcher(scope).matches())) {
            throw new IllegalStateException("Ainer bootstrap machine client scopes are invalid");
        }

        ServicePrincipal principal = servicePrincipalFoundationService.registerServicePrincipal(
                new IdentityAuthorityRef(properties.getIssuer()));
        servicePrincipalFoundationService.bindClient(principal.principalId(), clientId);
        RegisteredClient.Builder client = RegisteredClient.withId(dev.ainer.core.uuid.Uuidv7.generate().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Ainer bootstrap machine client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(AinerAuthorizationServerConfiguration.TOKEN_PROFILE_SETTING,
                                "SERVICE_V1")
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build());
        scopes.stream().sorted().forEach(client::scope);
        repository.save(client.build());
    }

    private String requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException("Ainer bootstrap machine " + name + " is invalid");
        }
        return value;
    }
}
