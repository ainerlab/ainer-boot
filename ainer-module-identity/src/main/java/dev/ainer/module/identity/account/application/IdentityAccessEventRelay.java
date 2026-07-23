package dev.ainer.module.identity.account.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class IdentityAccessEventRelay {

    public static final String UNEXPECTED_DELIVERY_ERROR =
            "AINER.IDENTITY.ACCESS_EVENT_DELIVERY_FAILED";

    private final IdentityAccessEventOutboxService outboxService;
    private final IdentityAccessEventPublisher publisher;
    private final Clock clock;

    public IdentityAccessEventRelay(
            IdentityAccessEventOutboxService outboxService,
            IdentityAccessEventPublisher publisher,
            Clock clock) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.clock = clock;
    }

    public IdentityAccessEventRelayResult relayBatch(
            String leaseOwner,
            Duration leaseDuration,
            Duration retryDelay,
            int maxAttempts,
            int batchSize) {
        Instant claimedAt = clock.instant();
        var deliveries = outboxService.claimBatch(
                leaseOwner, claimedAt, leaseDuration, maxAttempts, batchSize);
        int published = 0;
        int failed = 0;
        for (IdentityAccessEventDelivery delivery : deliveries) {
            try {
                publisher.publish(delivery.event());
                outboxService.markPublished(delivery.event().id(), leaseOwner, clock.instant());
                published++;
            } catch (IdentityAccessEventPublicationException exception) {
                outboxService.markFailed(
                        delivery.event().id(), leaseOwner,
                        clock.instant().plus(retryDelay), exception.errorCode());
                failed++;
            } catch (RuntimeException exception) {
                outboxService.markFailed(
                        delivery.event().id(), leaseOwner,
                        clock.instant().plus(retryDelay), UNEXPECTED_DELIVERY_ERROR);
                failed++;
            }
        }
        return new IdentityAccessEventRelayResult(deliveries.size(), published, failed);
    }
}
