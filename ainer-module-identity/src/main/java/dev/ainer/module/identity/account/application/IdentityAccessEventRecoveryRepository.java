package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityAccessEventRecoveryRepository {

    IdentityAccessEventOutboxPage findExhausted(
            UUID tenantId, Instant now, int maxAttempts, int page, int size, long offset);

    Optional<IdentityAccessEventOutboxEntry> findExhaustedForUpdate(
            UUID tenantId, UUID eventId, Instant now, int maxAttempts);

    void expireOpenRequests(UUID eventId, Instant now);

    void insertReplayRequest(IdentityAccessEventReplayRequest request);

    Optional<IdentityAccessEventReplayRequest> findReplayRequestForUpdate(
            UUID tenantId, UUID requestId);

    boolean resetExhaustedEvent(UUID tenantId, UUID eventId, Instant now, int maxAttempts);

    boolean markReplayExecuted(UUID requestId, String approvedBy, Instant executedAt);

    void insertOperationAudit(
            UUID operationId,
            UUID tenantId,
            UUID targetId,
            String phase,
            String actorServiceId,
            String incidentReference,
            Instant occurredAt);
}
