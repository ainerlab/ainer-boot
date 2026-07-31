package dev.ainer.authorizationserver.passkey;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerPasskeySettingsTest {

    @Test
    void acceptsHttpsRelyingPartyAndSubdomainOrigins() {
        AinerAuthorizationServerProperties properties = properties(
                "auth.ainer.dev",
                List.of("https://auth.ainer.dev", "https://admin.auth.ainer.dev:8443"));

        AinerPasskeySettings settings = AinerPasskeySettings.from(properties);

        assertThat(settings.rpId()).isEqualTo("auth.ainer.dev");
        assertThat(settings.allowedOrigins()).containsExactlyInAnyOrder(
                "https://auth.ainer.dev",
                "https://admin.auth.ainer.dev:8443");
        assertThat(settings.ceremonyTimeout()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsInsecureNonLoopbackOriginEvenWhenOverrideIsEnabled() {
        AinerAuthorizationServerProperties properties = properties(
                "auth.ainer.dev",
                List.of("http://auth.ainer.dev"),
                true,
                null);

        assertThatThrownBy(() -> AinerPasskeySettings.from(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void acceptsExplicitLocalhostHttpForAutomatedTestsOnly() {
        AinerAuthorizationServerProperties properties = properties(
                "localhost",
                List.of("http://localhost:9000"),
                true,
                null);

        assertThat(AinerPasskeySettings.from(properties).allowedOrigins())
                .containsExactly("http://localhost:9000");
    }

    @Test
    void rejectsOriginOutsideRelyingPartyScope() {
        AinerAuthorizationServerProperties properties = properties(
                "auth.ainer.dev",
                List.of("https://evil.example"));

        assertThatThrownBy(() -> AinerPasskeySettings.from(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subdomain");
    }

    @Test
    void rejectsIpAddressAsRelyingPartyId() {
        AinerAuthorizationServerProperties properties = properties(
                "127.0.0.1",
                List.of("https://127.0.0.1"));

        assertThatThrownBy(() -> AinerPasskeySettings.from(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DNS name");
    }

    @Test
    void rejectsPathDuplicateOriginAndExcessiveTimeout() {
        AinerAuthorizationServerProperties path = properties(
                "auth.ainer.dev",
                List.of("https://auth.ainer.dev/login"));
        assertThatThrownBy(() -> AinerPasskeySettings.from(path))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scheme, host and optional port");

        AinerAuthorizationServerProperties duplicate = properties(
                "auth.ainer.dev",
                List.of("https://auth.ainer.dev", "https://auth.ainer.dev/"));
        assertThatThrownBy(() -> AinerPasskeySettings.from(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicates");

        AinerAuthorizationServerProperties timeout = properties(
                "auth.ainer.dev",
                List.of("https://auth.ainer.dev"),
                false,
                Duration.ofMinutes(11));
        assertThatThrownBy(() -> AinerPasskeySettings.from(timeout))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 10 minutes");
    }

    private AinerAuthorizationServerProperties properties(String rpId, List<String> allowedOrigins) {
        return properties(rpId, allowedOrigins, false, null);
    }

    private AinerAuthorizationServerProperties properties(
            String rpId,
            List<String> allowedOrigins,
            boolean allowInsecureHttp,
            Duration ceremonyTimeout) {
        return new AinerAuthorizationServerProperties(
                null,
                null,
                null,
                new AinerAuthorizationServerProperties.Passkey(
                        true, rpId, "Ainer", allowedOrigins, allowInsecureHttp, ceremonyTimeout),
                null, null, null, null, null, null, null, null);
    }
}
