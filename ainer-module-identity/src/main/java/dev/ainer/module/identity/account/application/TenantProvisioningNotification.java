package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record TenantProvisioningNotification(
        TenantProvisioningNotificationType type,
        UUID provisioningRequestId,
        UUID tenantId,
        UUID subjectId,
        String deliveryChannel,
        String recipientReference,
        UUID activationGrantId,
        String activationSecret,
        Instant expiresAt) {

    private static final Pattern CHANNEL = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");

    public TenantProvisioningNotification {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (deliveryChannel == null || !CHANNEL.matcher(deliveryChannel).matches()) {
            throw new IllegalArgumentException("Notification delivery channel is invalid");
        }
        if (recipientReference == null
                || recipientReference.isBlank()
                || recipientReference.length() > 320) {
            throw new IllegalArgumentException("Notification recipient reference is invalid");
        }
        if (type == TenantProvisioningNotificationType.NEW_USER_ACTIVATION) {
            Objects.requireNonNull(activationGrantId, "activationGrantId");
            if (activationSecret == null
                    || activationSecret.length() < 43
                    || activationSecret.length() > 128) {
                throw new IllegalArgumentException("Activation secret is invalid");
            }
        } else if (activationGrantId != null || activationSecret != null) {
            throw new IllegalArgumentException(
                    "Existing-user acceptance notification cannot contain activation material");
        }
    }
}
