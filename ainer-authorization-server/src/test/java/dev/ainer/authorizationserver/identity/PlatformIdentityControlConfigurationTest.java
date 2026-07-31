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
                new PlatformIdentityControlProperties(
                        false,
                        List.of("platform-identity-operator"),
                        Duration.ofDays(7),
                        Duration.ofHours(24),
                        5,
                        "v1",
                        List.of("v1:" + Base64.getUrlEncoder().withoutPadding()
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
                new PlatformIdentityControlProperties(false, null, null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator-client-ids");
    }

    @Test
    void rejectsMissingOrInvalidNotificationProtectionKeyRing() {
        PlatformIdentityControlProperties missing =
                new PlatformIdentityControlProperties(
                        false, List.of("platform-identity-operator"), null, null, null, null, null);
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active key version");

        PlatformIdentityControlProperties invalid =
                new PlatformIdentityControlProperties(
                        false,
                        List.of("platform-identity-operator"),
                        null,
                        null,
                        null,
                        "v1",
                        List.of("v1:AQID"));
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key ring");
    }

    @Test
    void rejectsUnsafeOperatorAndOutOfRangeTtl() {
        PlatformIdentityControlProperties unsafe =
                new PlatformIdentityControlProperties(
                        false, List.of("unsafe operator"), null, null, null, null, null);
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(unsafe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operator client id");

        PlatformIdentityControlProperties longLived =
                new PlatformIdentityControlProperties(
                        false,
                        List.of("platform-identity-operator"),
                        Duration.ofDays(31),
                        null,
                        null,
                        null,
                        null);
        assertThatThrownBy(() -> configuration.platformIdentityControlSettings(longLived))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request-ttl");
    }
}
