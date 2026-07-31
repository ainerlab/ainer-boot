package dev.ainer.authorizationserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerIntrospectionClientBootstrapRunnerTest {

    @Test
    void createsDedicatedClientWithoutTenantOrBusinessScope() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        new AinerIntrospectionClientBootstrapRunner(properties, repository, encoder)
                .run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client = repository.findByClientId("introspection-client");
        assertThat(client.getScopes())
                .containsExactly(AinerAuthorizationServerConfiguration.INTROSPECTION_CLIENT_SCOPE);
        assertThat(client.getClientSettings().getSettings())
                .containsEntry(AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING, true)
                .doesNotContainKey(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING);
        assertThat(encoder.matches("introspection-client-secret-2026", client.getClientSecret())).isTrue();
    }

    @Test
    void weakSecretFailsClosed() {
        AinerAuthorizationServerProperties properties = withIntrospectionClientBootstrap(
                new AinerAuthorizationServerProperties.IntrospectionClientBootstrap(
                        true, "introspection-client", "too-short"));

        assertThatThrownBy(() -> new AinerIntrospectionClientBootstrapRunner(
                properties,
                new InMemoryRepository(),
                PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");
    }

    private AinerAuthorizationServerProperties properties() {
        return withIntrospectionClientBootstrap(
                new AinerAuthorizationServerProperties.IntrospectionClientBootstrap(
                        true, "introspection-client", "introspection-client-secret-2026"));
    }

    private static AinerAuthorizationServerProperties withIntrospectionClientBootstrap(
            AinerAuthorizationServerProperties.IntrospectionClientBootstrap bootstrap) {
        return new AinerAuthorizationServerProperties(
                null, null, null, null, null, bootstrap, null, null, null, null, null, null);
    }

    private static final class InMemoryRepository implements RegisteredClientRepository {

        private final Map<String, RegisteredClient> clients = new HashMap<>();

        @Override
        public void save(RegisteredClient registeredClient) {
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
