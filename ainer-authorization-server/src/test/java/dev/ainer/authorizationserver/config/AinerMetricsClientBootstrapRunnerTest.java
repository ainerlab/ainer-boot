package dev.ainer.authorizationserver.config;

import dev.ainer.security.AinerSecurityScopes;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerMetricsClientBootstrapRunnerTest {

    @Test
    void createsDedicatedTenantlessClientWithOneMinuteToken() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        AinerMetricsClientBootstrapRunner runner =
                new AinerMetricsClientBootstrapRunner(properties, repository, encoder);
        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client = repository.findByClientId("metrics-client");
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(client.getScopes()).containsExactly(AinerSecurityScopes.PLATFORM_METRICS_READ);
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientSettings().getSettings())
                .doesNotContainKey(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING)
                .doesNotContainKey(AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING);
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(1));
        assertThat(encoder.matches("metrics-client-secret-2026", client.getClientSecret())).isTrue();
    }

    @Test
    void weakSecretFailsClosed() {
        AinerAuthorizationServerProperties properties = properties();
        properties.getMetricsClientBootstrap().setClientSecret("too-short");

        assertThatThrownBy(() -> new AinerMetricsClientBootstrapRunner(
                properties,
                new InMemoryRepository(),
                PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");
    }

    private AinerAuthorizationServerProperties properties() {
        AinerAuthorizationServerProperties properties = new AinerAuthorizationServerProperties();
        AinerAuthorizationServerProperties.MetricsClientBootstrap bootstrap =
                properties.getMetricsClientBootstrap();
        bootstrap.setEnabled(true);
        bootstrap.setClientId("metrics-client");
        bootstrap.setClientSecret("metrics-client-secret-2026");
        return properties;
    }

    private static final class InMemoryRepository implements RegisteredClientRepository {

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
