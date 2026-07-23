package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceAuthorizationAuditCursor(Instant occurredAt, UUID id) {
    public WorkspaceAuthorizationAuditCursor {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(id, "id");
    }
}
