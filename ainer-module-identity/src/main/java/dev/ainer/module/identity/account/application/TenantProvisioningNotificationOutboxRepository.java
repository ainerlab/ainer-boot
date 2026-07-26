package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TenantProvisioningNotificationOutboxRepository {

    List<TenantProvisioningNotificationOutboxEntry> claimBatch(
            String leaseOwner,
            Instant now,
            Instant leaseUntil,
            int maxAttempts,
            int limit);

    boolean markPublished(UUID notificationId, String leaseOwner, Instant publishedAt);

    boolean markFailed(
            UUID notificationId,
            String leaseOwner,
            Instant availableAt,
            String errorCode);

    TenantProvisioningNotificationOutboxStatus status(int maxAttempts);
}
