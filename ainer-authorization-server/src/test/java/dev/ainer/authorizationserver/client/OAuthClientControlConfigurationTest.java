package dev.ainer.authorizationserver.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientControlConfigurationTest {

    private final OAuthClientControlConfiguration configuration =
            new OAuthClientControlConfiguration();

    @Test
    void acceptsBoundedTenantServiceClientPolicy() {
        OAuthClientControlProperties properties = new OAuthClientControlProperties(
                false,
                List.of("ainer-client-operator"),
                List.of("ai.invoke", "identity.directory.read"),
                null,
                null,
                null);

        OAuthClientControlSettings settings =
                configuration.oauthClientControlSettings(properties);

        assertThat(settings.operatorClientIds()).containsExactly("ainer-client-operator");
        assertThat(settings.allowedScopes()).containsExactlyInAnyOrder(
                "ai.invoke", "identity.directory.read");
        assertThat(settings.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.clientSecretTtl()).isEqualTo(Duration.ofDays(90));
        assertThat(settings.secretBytes()).isEqualTo(32);
    }

    @Test
    void rejectsMissingExactOperatorAllowlist() {
        OAuthClientControlProperties properties = new OAuthClientControlProperties(
                false, null, null, null, null, null);

        assertThatThrownBy(() -> configuration.oauthClientControlSettings(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator-client-ids");
    }

    @Test
    void rejectsPlatformAndCrossTenantScopes() {
        OAuthClientControlProperties properties = new OAuthClientControlProperties(
                false,
                List.of("ainer-client-operator"),
                List.of("platform.metrics.read", "identity.directory.read.all"),
                null,
                null,
                null);

        assertThatThrownBy(() -> configuration.oauthClientControlSettings(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved scope");
    }

    @Test
    void rejectsLongLivedAccessTokens() {
        OAuthClientControlProperties properties = new OAuthClientControlProperties(
                false,
                List.of("ainer-client-operator"),
                null,
                Duration.ofHours(1),
                null,
                null);

        assertThatThrownBy(() -> configuration.oauthClientControlSettings(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-ttl");
    }

    @Test
    void rejectsWeakGeneratedSecrets() {
        OAuthClientControlProperties properties = new OAuthClientControlProperties(
                false,
                List.of("ainer-client-operator"),
                null,
                null,
                null,
                16);

        assertThatThrownBy(() -> configuration.oauthClientControlSettings(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret-bytes");
    }
}
