package dev.ainer.module.identity.account.application;

import java.time.Instant;

public record TenantProvisioningNotificationOutboxStatus(
        long pending,
        long failed,
        long exhausted,
        long published,
        long cancelled,
        Instant oldestReadyAt) {
}
