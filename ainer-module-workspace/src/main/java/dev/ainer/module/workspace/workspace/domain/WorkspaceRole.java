package dev.ainer.module.workspace.workspace.domain;

public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageWorkspace() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canBeAssignedByMemberEndpoint() {
        return this != OWNER;
    }
}
