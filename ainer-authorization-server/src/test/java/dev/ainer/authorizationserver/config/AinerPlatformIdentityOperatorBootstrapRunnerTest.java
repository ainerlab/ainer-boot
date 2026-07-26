package dev.ainer.authorizationserver.config;

import dev.ainer.security.AinerSecurityScopes;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerPlatformIdentityOperatorBootstrapRunnerTest {

    @Test
    void createsDedicatedTenantlessOperatorWithExplicitScopesAndOneMinuteToken() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();

        AinerPlatformIdentityOperatorBootstrapRunner runner =
                new AinerPlatformIdentityOperatorBootstrapRunner(
                        properties, repository, encoder);
        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client =
                repository.findByClientId("platform-identity-operator");
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(client.getScopes()).containsExactlyInAnyOrder(
                AinerSecurityScopes.PLATFORM_TENANTS_READ,
                AinerSecurityScopes.PLATFORM_TENANTS_WRITE,
                AinerSecurityScopes.PLATFORM_USERS_READ,
                AinerSecurityScopes.PLATFORM_USERS_WRITE);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientSettings().getSettings())
                .doesNotContainKey(
                        AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING)
                .doesNotContainKey(
                        AinerAuthorizationServerConfiguration
                                .CLIENT_INTROSPECTION_ALLOWED_SETTING);
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(1));
        assertThat(encoder.matches(
                "platform-identity-secret-2026",
                client.getClientSecret())).isTrue();
    }

    @Test
    void weakSecretFailsClosed() {
        AinerAuthorizationServerProperties properties = properties();
        properties.getPlatformIdentityOperatorBootstrap()
                .setClientSecret("too-short");

        assertThatThrownBy(() ->
                new AinerPlatformIdentityOperatorBootstrapRunner(
                        properties,
                        new InMemoryRepository(),
                        PasswordEncoderFactories.createDelegatingPasswordEncoder())
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");
    }

    @Test
    void incompatibleExistingClientFailsClosed() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(RegisteredClient.withId("existing-id")
                .clientId("platform-identity-operator")
                .clientSecret("{noop}existing-secret")
                .clientName("Incorrect operator")
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core
                                .ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerSecurityScopes.PLATFORM_TENANTS_READ)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build());

        assertThatThrownBy(() ->
                new AinerPlatformIdentityOperatorBootstrapRunner(
                        properties,
                        repository,
                        PasswordEncoderFactories.createDelegatingPasswordEncoder())
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    private AinerAuthorizationServerProperties properties() {
        AinerAuthorizationServerProperties properties =
                new AinerAuthorizationServerProperties();
        AinerAuthorizationServerProperties.PlatformIdentityOperatorBootstrap bootstrap =
                properties.getPlatformIdentityOperatorBootstrap();
        bootstrap.setEnabled(true);
        bootstrap.setClientId("platform-identity-operator");
        bootstrap.setClientSecret("platform-identity-secret-2026");
        return properties;
    }

    private static final class InMemoryRepository
            implements RegisteredClientRepository {

        private final Map<String, RegisteredClient> clients = new HashMap<>();
        private int saveCount;

        @Override
        public void save(RegisteredClient registeredClient) {
            saveCount++;
            clients.put(registeredClient.getClientId(), registeredClient);
        }

        @Override
        public RegisteredClient findById(String id) {
            return clients.values().stream()
                    .filter(client -> client.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public RegisteredClient findByClientId(String clientId) {
            return clients.get(clientId);
        }
    }
}
