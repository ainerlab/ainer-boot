package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.infrastructure.security.AesGcmTenantProvisioningNotificationPayloadProtector;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisioningNotificationProtectionTest {

    private static final byte[] KEY_V1 = new byte[32];
    private static final byte[] KEY_V2 = new byte[] {
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1
    };

    @Test
    void roundTripsActivationMaterialWithoutPersistingPlaintext() {
        TenantProvisioningNotification notification = notification();
        var protector = protector("v1", Map.of("v1", KEY_V1));

        ProtectedTenantProvisioningNotification protectedNotification =
                protector.protect(notification);

        assertThat(protectedNotification.keyVersion()).isEqualTo("v1");
        assertThat(new String(
                protectedNotification.payload(), StandardCharsets.ISO_8859_1))
                .doesNotContain(notification.activationSecret())
                .doesNotContain(notification.recipientReference());
        assertThat(protector.unprotect(protectedNotification)).isEqualTo(notification);
    }

    @Test
    void oldPayloadRemainsReadableDuringKeyRotation() {
        ProtectedTenantProvisioningNotification protectedNotification =
                protector("v1", Map.of("v1", KEY_V1)).protect(notification());
        var rotated = protector(
                "v2",
                Map.of("v1", KEY_V1, "v2", KEY_V2));

        assertThat(rotated.unprotect(protectedNotification)).isEqualTo(notification());
        assertThat(rotated.protect(notification()).keyVersion()).isEqualTo("v2");
    }

    @Test
    void rejectsTamperedCiphertextAndUnknownKeyVersion() {
        var protector = protector("v1", Map.of("v1", KEY_V1));
        ProtectedTenantProvisioningNotification original =
                protector.protect(notification());
        byte[] tampered = original.payload();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> protector.unprotect(
                new ProtectedTenantProvisioningNotification("v1", tampered)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication failed");
        assertThatThrownBy(() -> protector.unprotect(
                new ProtectedTenantProvisioningNotification(
                        "missing", original.payload())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
    }

    private static AesGcmTenantProvisioningNotificationPayloadProtector protector(
            String activeVersion,
            Map<String, byte[]> keys) {
        return new AesGcmTenantProvisioningNotificationPayloadProtector(
                activeVersion, keys, new SecureRandom());
    }

    private static TenantProvisioningNotification notification() {
        return new TenantProvisioningNotification(
                TenantProvisioningNotificationType.NEW_USER_ACTIVATION,
                UUID.fromString("019c0000-0000-7000-8000-000000000001"),
                UUID.fromString("019c0000-0000-7000-8000-000000000002"),
                UUID.fromString("019c0000-0000-7000-8000-000000000003"),
                "EMAIL",
                "owner@example.com",
                UUID.fromString("019c0000-0000-7000-8000-000000000004"),
                "0123456789012345678901234567890123456789012",
                Instant.parse("2026-07-27T00:00:00Z"));
    }
}
