package dev.ainer.authorizationserver.config;

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

class AinerClientControlOperatorBootstrapRunnerTest {

    @Test
    void createsDedicatedTenantlessOperatorWithOneMinuteToken() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        AinerClientControlOperatorBootstrapRunner runner =
                new AinerClientControlOperatorBootstrapRunner(properties, repository, encoder);
        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client = repository.findByClientId("client-control-operator");
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(client.getScopes()).containsExactly(
                AinerAuthorizationServerConfiguration.CLIENT_CONTROL_MANAGE_SCOPE);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientSettings().getSettings())
                .doesNotContainKey(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING)
                .doesNotContainKey(
                        AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING);
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(1));
        assertThat(encoder.matches(
                "client-control-operator-secret-2026", client.getClientSecret())).isTrue();
    }

    @Test
    void weakSecretFailsClosed() {
        AinerAuthorizationServerProperties properties = withClientControlOperatorBootstrap(
                new AinerAuthorizationServerProperties.ClientControlOperatorBootstrap(
                        true, "client-control-operator", "too-short"));

        assertThatThrownBy(() -> new AinerClientControlOperatorBootstrapRunner(
                properties,
                new InMemoryRepository(),
                PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");
    }

    private AinerAuthorizationServerProperties properties() {
        return withClientControlOperatorBootstrap(
                new AinerAuthorizationServerProperties.ClientControlOperatorBootstrap(
                        true, "client-control-operator", "client-control-operator-secret-2026"));
    }

    private static AinerAuthorizationServerProperties withClientControlOperatorBootstrap(
            AinerAuthorizationServerProperties.ClientControlOperatorBootstrap bootstrap) {
        return new AinerAuthorizationServerProperties(
                null, null, null, null, null, null, null, bootstrap, null, null, null, null);
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
