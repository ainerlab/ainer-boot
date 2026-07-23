package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxEntry;

import java.time.Instant;
import java.util.UUID;

public record IdentityAccessEventOutboxResponse(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID subjectId,
        int payloadVersion,
        Instant occurredAt,
        String publicationStatus,
        int attemptCount,
        Instant availableAt,
        String lastErrorCode) {

    static IdentityAccessEventOutboxResponse from(IdentityAccessEventOutboxEntry entry) {
        return new IdentityAccessEventOutboxResponse(
                entry.eventId(), entry.eventType().name(), entry.tenantId(), entry.subjectId(),
                entry.payloadVersion(), entry.occurredAt(), entry.publicationStatus(),
                entry.attemptCount(), entry.availableAt(), entry.lastErrorCode());
    }
}
