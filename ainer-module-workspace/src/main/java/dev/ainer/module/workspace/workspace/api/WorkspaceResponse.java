package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.domain.Workspace;

import java.time.Instant;
import java.util.UUID;

    public record WorkspaceResponse(
        UUID id,
        String name,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.id(),
                workspace.name().value(),
                workspace.version(),
                workspace.createdAt(),
                workspace.updatedAt());
    }
}
