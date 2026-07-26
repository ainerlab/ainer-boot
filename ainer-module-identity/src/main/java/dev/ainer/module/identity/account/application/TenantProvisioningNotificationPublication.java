package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningNotificationPublication(
        UUID notificationId,
        String publicationStatus,
        Instant publishedAt) {
}
