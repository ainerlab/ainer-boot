package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.domain.IdentityAccessEvent;

import java.time.Instant;
import java.util.UUID;

public record IdentityAccessEventRequest(
        UUID eventId,
        String eventType,
        UUID tenantId,
        UUID subjectId,
        int payloadVersion,
        Instant occurredAt) {

    static IdentityAccessEventRequest from(IdentityAccessEvent event) {
        return new IdentityAccessEventRequest(
                event.id(),
                event.type().name(),
                event.tenantId(),
                event.subjectId(),
                event.payloadVersion(),
                event.occurredAt());
    }
}
