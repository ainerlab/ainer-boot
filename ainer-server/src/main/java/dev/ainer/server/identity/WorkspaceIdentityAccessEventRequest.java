package dev.ainer.server.identity;

import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEvent;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceIdentityAccessEventRequest(
        @NotNull UUID eventId,
        @NotNull WorkspaceIdentityAccessEventType eventType,
        @NotNull UUID subjectId,
        @Min(1) @Max(1) int payloadVersion,
        @NotNull Instant occurredAt) {

    WorkspaceIdentityAccessEvent toEvent() {
        return new WorkspaceIdentityAccessEvent(
                eventId, eventType, subjectId, payloadVersion, occurredAt);
    }
}
