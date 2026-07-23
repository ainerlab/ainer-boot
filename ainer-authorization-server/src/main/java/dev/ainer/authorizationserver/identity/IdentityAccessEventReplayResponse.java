package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventReplayRequest;

import java.time.Instant;
import java.util.UUID;

public record IdentityAccessEventReplayResponse(
        UUID requestId,
        UUID eventId,
        UUID tenantId,
        String requestedBy,
        String approvedBy,
        String incidentReference,
        String status,
        Instant requestedAt,
        Instant expiresAt,
        Instant executedAt) {

    static IdentityAccessEventReplayResponse from(IdentityAccessEventReplayRequest request) {
        return new IdentityAccessEventReplayResponse(
                request.id(), request.eventId(), request.tenantId(), request.requestedBy(),
                request.approvedBy(), request.incidentReference(), request.status(),
                request.requestedAt(), request.expiresAt(), request.executedAt());
    }
}
