package dev.ainer.authorizationserver.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerAdminBrowserClientBootstrapRunnerTest {

    @Test
    void createsExactPublicPkceClientWithoutRefreshToken() {
        AinerAdminBrowserClientProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();

        new AinerAdminBrowserClientBootstrapRunner(properties, repository)
                .run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client = repository.findByClientId("ainer-admin-dev");
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE)
                .doesNotContain(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getRedirectUris())
                .containsExactly("http://127.0.0.1:5173/ainer-admin/auth/callback");
        assertThat(client.getPostLogoutRedirectUris())
                .containsExactly("http://127.0.0.1:5173/ainer-admin/auth/logged-out");
        assertThat(client.getScopes()).containsExactlyInAnyOrder(
                "openid",
                "profile",
                "tenant.members.read",
                "tenant.members.write");
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
    }

    @Test
    void repeatedBootstrapAcceptsOnlyIdenticalPolicy() {
        AinerAdminBrowserClientProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        AinerAdminBrowserClientBootstrapRunner runner =
                new AinerAdminBrowserClientBootstrapRunner(properties, repository);
        runner.run(new DefaultApplicationArguments(new String[0]));

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(repository.clients).hasSize(1);
    }

    @Test
    void existingPolicyDriftFailsClosed() {
        AinerAdminBrowserClientProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(RegisteredClient.withId("drifted")
                .clientId("ainer-admin-dev")
                .clientName("Drifted")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.getRedirectUri())
                .scope("openid")
                .build());

        assertThatThrownBy(() -> new AinerAdminBrowserClientBootstrapRunner(properties, repository)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public PKCE policy");
    }

    @Test
    void redirectContractRejectsCrossOriginAndUnsafeHttp() {
        AinerAdminBrowserClientProperties crossOrigin = properties();
        crossOrigin.setPostLogoutRedirectUri(
                "http://localhost:5173/ainer-admin/auth/logged-out");
        assertThatThrownBy(() -> new AinerAdminBrowserClientBootstrapRunner(
                crossOrigin, new InMemoryRepository())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same origin");

        AinerAdminBrowserClientProperties unsafe = properties();
        unsafe.setRedirectUri("http://admin.example/ainer-admin/auth/callback");
        unsafe.setPostLogoutRedirectUri("http://admin.example/ainer-admin/auth/logged-out");
        assertThatThrownBy(() -> new AinerAdminBrowserClientBootstrapRunner(
                unsafe, new InMemoryRepository())
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS URI");
    }

    private AinerAdminBrowserClientProperties properties() {
        AinerAdminBrowserClientProperties properties = new AinerAdminBrowserClientProperties();
        properties.setEnabled(true);
        properties.setRedirectUri(
                "http://127.0.0.1:5173/ainer-admin/auth/callback");
        properties.setPostLogoutRedirectUri(
                "http://127.0.0.1:5173/ainer-admin/auth/logged-out");
        return properties;
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
