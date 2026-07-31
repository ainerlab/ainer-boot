package dev.ainer.authorizationserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerMachineClientBootstrapRunnerTest {

    @Test
    void createsExplicitTenantScopedClientWithoutStoringRawSecret() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        new AinerMachineClientBootstrapRunner(properties, repository, encoder)
                .run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client = repository.findByClientId("machine-client");
        assertThat(client).isNotNull();
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getScopes()).containsExactly("ai.invoke");
        assertThat(client.getClientSettings().getSetting(
                AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING).toString())
                .isEqualTo("tenant:machine");
        assertThat(client.getClientSettings().getSettings())
                .doesNotContainKey(AinerAuthorizationServerConfiguration.CLIENT_INTROSPECTION_ALLOWED_SETTING);
        assertThat(client.getClientSecret()).isNotEqualTo("machine-client-secret-2026");
        assertThat(encoder.matches("machine-client-secret-2026", client.getClientSecret())).isTrue();
    }

    @Test
    void existingClientIsNotRotatedByAnotherStartup() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AinerMachineClientBootstrapRunner runner = new AinerMachineClientBootstrapRunner(properties, repository, encoder);

        runner.run(new DefaultApplicationArguments(new String[0]));
        String firstHash = repository.findByClientId("machine-client").getClientSecret();
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(repository.findByClientId("machine-client").getClientSecret()).isEqualTo(firstHash);
        assertThat(repository.clients).hasSize(1);
    }

    @Test
    void weakBootstrapSecretFailsClosed() {
        AinerAuthorizationServerProperties properties = withMachineClientBootstrap(
                new AinerAuthorizationServerProperties.MachineClientBootstrap(
                        true, "machine-client", "too-short", "tenant:machine", null));

        assertThatThrownBy(() -> new AinerMachineClientBootstrapRunner(
                properties,
                new InMemoryRepository(),
                PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");
    }

    private AinerAuthorizationServerProperties properties() {
        return withMachineClientBootstrap(new AinerAuthorizationServerProperties.MachineClientBootstrap(
                true, "machine-client", "machine-client-secret-2026", "tenant:machine", null));
    }

    private static AinerAuthorizationServerProperties withMachineClientBootstrap(
            AinerAuthorizationServerProperties.MachineClientBootstrap bootstrap) {
        return new AinerAuthorizationServerProperties(
                null, null, null, null, bootstrap, null, null, null, null, null, null, null);
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
