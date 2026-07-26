package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningNotificationReceiptCommand(
        String eventId,
        UUID notificationId,
        TenantProvisioningNotificationDeliveryStatus status,
        Instant occurredAt,
        String failureCode) {
}
