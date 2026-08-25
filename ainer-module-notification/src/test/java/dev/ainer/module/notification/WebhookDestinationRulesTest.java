package dev.ainer.module.notification;

import dev.ainer.module.notification.notification.application.NotificationWebhookProperties;
import dev.ainer.module.notification.notification.application.WebhookDestinationRules;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDestinationRulesTest {

    @Test
    void acceptsHttpsHostOnAllowlist() {
        var properties = properties(false, "8.8.8.8");

        assertThat(WebhookDestinationRules.validate("https://8.8.8.8/hooks/notify", properties).getHost())
                .isEqualTo("8.8.8.8");
    }

    @Test
    void rejectsHostOutsideAllowlist() {
        var properties = properties(false, "example.test");

        assertRejected("https://other.test/hooks/notify", properties);
    }

    @Test
    void rejectsHttpUnlessInsecureLoopback() {
        var properties = properties(false, "example.test");

        assertRejected("http://example.test/hooks/notify", properties);
    }

    @Test
    void rejectsLoopbackWhenInsecureHttpDisabled() {
        var properties = properties(false, "127.0.0.1");

        assertRejected("https://127.0.0.1/hooks", properties);
        assertRejected("http://127.0.0.1/hooks", properties);
    }

    @Test
    void acceptsLoopbackHttpWhenExplicitlyAllowed() {
        var properties = properties(true, "127.0.0.1");

        assertThat(WebhookDestinationRules.validate("http://127.0.0.1:8080/hook", properties).getPort())
                .isEqualTo(8080);
    }

    @Test
    void rejectsUserInfoAndFragment() {
        var properties = properties(false, "example.test");

        assertRejected("https://user:pass@example.test/hooks", properties);
        assertRejected("https://example.test/hooks#frag", properties);
    }

    @Test
    void rejectsPrivateLiteralAddressesEvenIfListed() {
        var properties = properties(false, "10.0.0.1");

        assertRejected("https://10.0.0.1/hooks", properties);
    }

    @Test
    void rejectsLinkLocalMetadataAddress() {
        var properties = properties(false, "169.254.169.254");

        assertRejected("https://169.254.169.254/latest/meta-data", properties);
    }

    @Test
    void exceptionMessageDoesNotEchoRecipient() {
        var properties = properties(false, "example.test");
        String recipient = "https://leaked.example/hooks?token=secret-value";

        assertThatThrownBy(() -> WebhookDestinationRules.validate(recipient, properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook destination is not allowed")
                .hasMessageNotContaining("secret-value")
                .hasMessageNotContaining(recipient);
    }

    @Test
    void enabledPropertiesRequireAllowlist() {
        assertThatThrownBy(() -> new NotificationWebhookProperties(
                true, List.of(), Duration.ofSeconds(2), Duration.ofSeconds(2), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-hosts");
    }

    private static void assertRejected(String recipient, NotificationWebhookProperties properties) {
        assertThatThrownBy(() -> WebhookDestinationRules.validate(recipient, properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook destination is not allowed");
    }

    private static NotificationWebhookProperties properties(boolean allowInsecureHttp, String... hosts) {
        return new NotificationWebhookProperties(
                true, List.of(hosts), Duration.ofSeconds(2), Duration.ofSeconds(2), allowInsecureHttp);
    }
}
