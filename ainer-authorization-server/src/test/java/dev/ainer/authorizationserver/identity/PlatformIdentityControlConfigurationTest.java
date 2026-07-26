package dev.ainer.authorizationserver.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformIdentityControlConfigurationTest {

    private final PlatformIdentityControlConfiguration configuration =
            new PlatformIdentityControlConfiguration();

    @Test
    void acceptsExactOperatorsAndBoundedRequestTtl() {
        PlatformIdentityControlProperties properties =
                new PlatformIdentityControlProperties();
        properties.setOperatorClientIds(List.of("platform-identity-operator"));
        properties.setRequestTtl(Duration.ofDays(7));
        properties.setActivationTtl(Duration.ofHours(24));
        properties.setActivationMaxAttempts(5);
        properties.setNotificationProtectionActiveKeyVersion("v1");
        properties.setNotificationProtectionKeys(List.of(
                "v1:" + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(new byte[32])));

        PlatformIdentityControlSettings settings =
                configuration.platformIdentityControlSettings(properties);

        assertThat(settings.operatorClientIds())
                .containsExactly("platform-identity-operator");
        assertThat(settings.policy().requestTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(settings.policy().activationTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(settings.policy().activationMaxAttempts()).isEqualTo(5);
        assertThat(settings.notificationProtectionActiveKeyVersion()).isEqualTo("v1");
        assertThat(settings.notificationProtectionKeys().get("v1")).hasSize(32);
    }

    @Test
    void rejectsMissingOperatorAllowlist() {
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(
                new PlatformIdentityControlProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator-client-ids");
    }

    @Test
    void rejectsMissingOrInvalidNotificationProtectionKeyRing() {
        PlatformIdentityControlProperties missing =
                new PlatformIdentityControlProperties();
        missing.setOperatorClientIds(List.of("platform-identity-operator"));
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active key version");

        PlatformIdentityControlProperties invalid =
                new PlatformIdentityControlProperties();
        invalid.setOperatorClientIds(List.of("platform-identity-operator"));
        invalid.setNotificationProtectionActiveKeyVersion("v1");
        invalid.setNotificationProtectionKeys(List.of("v1:AQID"));
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key ring");
    }

    @Test
    void rejectsUnsafeOperatorAndOutOfRangeTtl() {
        PlatformIdentityControlProperties unsafe =
                new PlatformIdentityControlProperties();
        unsafe.setOperatorClientIds(List.of("unsafe operator"));
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(unsafe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator client id");

        PlatformIdentityControlProperties longLived =
                new PlatformIdentityControlProperties();
        longLived.setOperatorClientIds(List.of("platform-identity-operator"));
        longLived.setRequestTtl(Duration.ofDays(31));
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(longLived))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request-ttl");
    }
}
