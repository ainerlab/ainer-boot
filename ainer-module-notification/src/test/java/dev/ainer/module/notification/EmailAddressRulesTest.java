package dev.ainer.module.notification;

import dev.ainer.module.notification.notification.application.EmailAddressRules;
import dev.ainer.module.notification.notification.application.NotificationEmailProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressRulesTest {

    @Test
    void acceptsOrdinaryAddress() {
        assertThat(EmailAddressRules.validate("ops@example.test")).isEqualTo("ops@example.test");
    }

    @Test
    void rejectsMissingAtOrDomainDot() {
        assertRejected("ops-at-example.test");
        assertRejected("ops@localhost");
        assertRejected("@example.test");
        assertRejected("ops@");
        assertRejected("ops @example.test");
        assertRejected("ops@exam ple.test");
    }

    @Test
    void rejectsHeaderInjection() {
        assertRejected("ops@example.test\r\nBcc: evil@example.test");
        assertThatThrownBy(() -> EmailAddressRules.requireSafeText("Hello\nX-Inject: 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email content is not allowed");
    }

    @Test
    void exceptionDoesNotEchoAddress() {
        String address = "secret-user@example.test\nBcc: leak@example.test";
        assertThatThrownBy(() -> EmailAddressRules.validate(address))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email address is not allowed")
                .hasMessageNotContaining("secret-user")
                .hasMessageNotContaining(address);
    }

    @Test
    void enabledPropertiesRequireFrom() {
        assertThatThrownBy(() -> new NotificationEmailProperties(true, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email address is not allowed");
    }

    private static void assertRejected(String address) {
        assertThatThrownBy(() -> EmailAddressRules.validate(address))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email address is not allowed");
    }
}
