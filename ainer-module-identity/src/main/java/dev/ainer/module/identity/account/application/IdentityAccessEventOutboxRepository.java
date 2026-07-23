package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityAccessEventOutboxRepository {

    List<IdentityAccessEventDelivery> claimBatch(
            String leaseOwner,
            Instant now,
            Instant leaseUntil,
            int maxAttempts,
            int limit);

    boolean markPublished(UUID eventId, String leaseOwner, Instant publishedAt);

    boolean markFailed(
            UUID eventId,
            String leaseOwner,
            Instant availableAt,
            String errorCode);

    IdentityAccessEventOutboxStatus status(int maxAttempts);
}
