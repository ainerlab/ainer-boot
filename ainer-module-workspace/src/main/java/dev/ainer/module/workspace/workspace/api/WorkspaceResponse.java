package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.domain.Workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String tenantId,
        String name,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.id(),
                workspace.tenantId().value(),
                workspace.name().value(),
                workspace.version(),
                workspace.createdAt(),
                workspace.updatedAt());
    }
}
