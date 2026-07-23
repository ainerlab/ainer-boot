package dev.ainer.server.identity;

import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventResult;

import java.util.UUID;

public record WorkspaceIdentityAccessEventResponse(
        UUID eventId,
        boolean duplicate,
        int affectedMemberships) {

    static WorkspaceIdentityAccessEventResponse from(
            UUID eventId,
            WorkspaceIdentityAccessEventResult result) {
        return new WorkspaceIdentityAccessEventResponse(
                eventId, result.duplicate(), result.affectedMemberships());
    }
}
