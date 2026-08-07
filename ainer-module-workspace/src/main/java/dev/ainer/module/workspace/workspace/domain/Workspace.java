package dev.ainer.module.workspace.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workspace(
        UUID id,
        WorkspaceName name,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Workspace {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("Workspace version cannot be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Workspace update time cannot precede creation time");
        }
    }

    public static Workspace create(UUID id, WorkspaceName name, Instant now) {
        return new Workspace(id, name, 0, now, now);
    }

    public Workspace rename(WorkspaceName newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(now, "now");
        if (name.equals(newName)) {
            return this;
        }
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Workspace update time cannot move backwards");
        }
        return new Workspace(id, newName, version + 1, createdAt, now);
    }
}
