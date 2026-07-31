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

class AinerProvisioningNotificationReceiptClientBootstrapRunnerTest {

    @Test
    void createsDedicatedTenantlessReceiptClientWithOneScopeAndShortToken() {
        AinerAuthorizationServerProperties properties = properties();
        InMemoryRepository repository = new InMemoryRepository();
        PasswordEncoder encoder =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AinerProvisioningNotificationReceiptClientBootstrapRunner runner =
                new AinerProvisioningNotificationReceiptClientBootstrapRunner(
                        properties, repository, encoder);

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        RegisteredClient client =
                repository.findByClientId("provisioning-notification-receipt");
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(client.getScopes()).containsExactly(
                AinerSecurityScopes
                        .IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_WRITE);
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
                "notification-receipt-secret-2026",
                client.getClientSecret())).isTrue();
    }

    @Test
    void weakSecretAndIncompatibleExistingClientFailClosed() {
        AinerAuthorizationServerProperties weak =
                withProvisioningNotificationReceiptClientBootstrap(
                        new AinerAuthorizationServerProperties
                                .ProvisioningNotificationReceiptClientBootstrap(
                                true, "provisioning-notification-receipt", "too-short"));
        assertThatThrownBy(() ->
                new AinerProvisioningNotificationReceiptClientBootstrapRunner(
                        weak,
                        new InMemoryRepository(),
                        PasswordEncoderFactories.createDelegatingPasswordEncoder())
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 to 128");

        AinerAuthorizationServerProperties incompatible = properties();
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(RegisteredClient.withId("existing-id")
                .clientId("provisioning-notification-receipt")
                .clientSecret("{noop}existing-secret")
                .clientName("Incorrect receipt gateway")
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core
                                .ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(AinerSecurityScopes
                        .IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build());

        assertThatThrownBy(() ->
                new AinerProvisioningNotificationReceiptClientBootstrapRunner(
                        incompatible,
                        repository,
                        PasswordEncoderFactories.createDelegatingPasswordEncoder())
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    private AinerAuthorizationServerProperties properties() {
        return withProvisioningNotificationReceiptClientBootstrap(
                new AinerAuthorizationServerProperties
                        .ProvisioningNotificationReceiptClientBootstrap(
                        true, "provisioning-notification-receipt", "notification-receipt-secret-2026"));
    }

    private static AinerAuthorizationServerProperties withProvisioningNotificationReceiptClientBootstrap(
            AinerAuthorizationServerProperties.ProvisioningNotificationReceiptClientBootstrap bootstrap) {
        return new AinerAuthorizationServerProperties(
                null, null, null, null, null, null, null, null, null, null, null, bootstrap);
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
