package dev.ainer.module.workspace.workspace.application;

public record WorkspaceIdentityAccessEventResult(boolean duplicate, int affectedMemberships) {

    public WorkspaceIdentityAccessEventResult {
        if (affectedMemberships < 0) {
            throw new IllegalArgumentException("Affected membership count cannot be negative");
        }
    }
}
