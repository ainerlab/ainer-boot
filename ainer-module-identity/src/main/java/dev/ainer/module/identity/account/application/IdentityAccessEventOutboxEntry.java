package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityAccessEventType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentityAccessEventOutboxEntry(
        UUID eventId,
        IdentityAccessEventType eventType,
        UUID tenantId,
        UUID subjectId,
        int payloadVersion,
        Instant occurredAt,
        String publicationStatus,
        int attemptCount,
        Instant availableAt,
        Instant leaseUntil,
        String lastErrorCode) {

    public IdentityAccessEventOutboxEntry {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(publicationStatus, "publicationStatus");
        Objects.requireNonNull(availableAt, "availableAt");
    }
}
