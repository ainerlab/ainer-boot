package dev.ainer.module.identity.account.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class TenantProvisioningNotificationRelay {

    private static final String PROTECTION_ERROR =
            "AINER.IDENTITY.NOTIFICATION_PAYLOAD_INVALID";
    private static final String PUBLICATION_ERROR =
            "AINER.IDENTITY.NOTIFICATION_DELIVERY_FAILED";

    private final TenantProvisioningNotificationOutboxService outboxService;
    private final TenantProvisioningNotificationPayloadProtector protector;
    private final TenantProvisioningNotificationPublisher publisher;
    private final Clock clock;

    public TenantProvisioningNotificationRelay(
            TenantProvisioningNotificationOutboxService outboxService,
            TenantProvisioningNotificationPayloadProtector protector,
            TenantProvisioningNotificationPublisher publisher,
            Clock clock) {
        this.outboxService = outboxService;
        this.protector = protector;
        this.publisher = publisher;
        this.clock = clock;
    }

    public TenantProvisioningNotificationRelayResult relay(
            String leaseOwner,
            Duration leaseDuration,
            Duration retryDelay,
            int maxAttempts,
            int batchSize) {
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("Notification retry delay must be positive");
        }
        var deliveries = outboxService.claimBatch(
                leaseOwner, clock.instant(), leaseDuration, maxAttempts, batchSize);
        int published = 0;
        int failed = 0;
        for (TenantProvisioningNotificationOutboxEntry delivery : deliveries) {
            try {
                TenantProvisioningNotification notification =
                        protector.unprotect(delivery.protectedNotification());
                requireMatchingEnvelope(delivery, notification);
                publisher.publish(new TenantProvisioningNotificationDelivery(
                        delivery.id(), delivery.templateVersion(), notification));
                outboxService.markPublished(delivery.id(), leaseOwner, clock.instant());
                published++;
            } catch (TenantProvisioningNotificationPublicationException exception) {
                outboxService.markFailed(
                        delivery.id(),
                        leaseOwner,
                        clock.instant().plus(retryDelay),
                        exception.errorCode());
                failed++;
            } catch (RuntimeException exception) {
                String errorCode = exception instanceof IllegalStateException
                        ? PROTECTION_ERROR
                        : PUBLICATION_ERROR;
                outboxService.markFailed(
                        delivery.id(),
                        leaseOwner,
                        clock.instant().plus(retryDelay),
                        errorCode);
                failed++;
            }
        }
        return new TenantProvisioningNotificationRelayResult(
                deliveries.size(), published, failed);
    }

    private void requireMatchingEnvelope(
            TenantProvisioningNotificationOutboxEntry entry,
            TenantProvisioningNotification notification) {
        if (!entry.provisioningRequestId().equals(notification.provisioningRequestId())
                || !entry.tenantId().equals(notification.tenantId())
                || !entry.subjectId().equals(notification.subjectId())
                || entry.type() != notification.type()) {
            throw new IllegalStateException(
                    "Protected notification does not match its outbox envelope");
        }
    }
}
