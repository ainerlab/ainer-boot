package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceIdentityAccessEvent(
        UUID eventId,
        WorkspaceIdentityAccessEventType eventType,
        UUID tenantId,
        UUID subjectId,
        int payloadVersion,
        Instant occurredAt) {

    public static final int CURRENT_PAYLOAD_VERSION = 1;

    public WorkspaceIdentityAccessEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        if (payloadVersion != CURRENT_PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported identity access event payload version");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
