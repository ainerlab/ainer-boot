package dev.ainer.authorizationserver.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisioningNotificationRelayConfigurationTest {

    private final TenantProvisioningNotificationRelayConfiguration configuration =
            new TenantProvisioningNotificationRelayConfiguration();

    @Test
    void acceptsHttpsAndExplicitLocalHttpUris() {
        assertThat(configuration.requireUri(
                "https://notify.example.com/internal/identity/notifications",
                false,
                "gateway-uri"))
                .hasScheme("https")
                .hasHost("notify.example.com");
        assertThat(configuration.requireUri(
                "http://127.0.0.1:9090/oauth2/token",
                true,
                "token-uri"))
                .hasScheme("http")
                .hasHost("127.0.0.1");
    }

    @Test
    void rejectsInsecureOrAmbiguousUrisAndInvalidRelayBounds() {
        assertThatThrownBy(() -> configuration.requireUri(
                "http://notify.example.com/internal/identity/notifications",
                false,
                "gateway-uri"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway-uri");
        assertThatThrownBy(() -> configuration.requireUri(
                "http://notify.example.com/internal/identity/notifications",
                true,
                "gateway-uri"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway-uri");
        assertThatThrownBy(() -> configuration.requireUri(
                "https://notify.example.com/notifications?secret=value",
                false,
                "gateway-uri"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway-uri");

        TenantProvisioningNotificationRelayProperties invalid =
                new TenantProvisioningNotificationRelayProperties(
                        false, null, null, null, null, null, false, null, null, Duration.ZERO, null, null);
        assertThatThrownBy(() -> configuration.validateSettings(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("settings");
    }
}
