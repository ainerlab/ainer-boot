package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningNotificationReceipt(
        UUID id,
        UUID notificationId,
        String gatewayClientId,
        String gatewayEventId,
        TenantProvisioningNotificationDeliveryStatus status,
        String failureCode,
        Instant occurredAt,
        Instant receivedAt,
        String requestId) {
}
