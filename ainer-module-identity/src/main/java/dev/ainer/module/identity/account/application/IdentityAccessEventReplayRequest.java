package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentityAccessEventReplayRequest(
        UUID id,
        UUID eventId,
        UUID tenantId,
        String requestedBy,
        String approvedBy,
        String incidentReference,
        String status,
        Instant requestedAt,
        Instant expiresAt,
        Instant executedAt) {

    public IdentityAccessEventReplayRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(incidentReference, "incidentReference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
