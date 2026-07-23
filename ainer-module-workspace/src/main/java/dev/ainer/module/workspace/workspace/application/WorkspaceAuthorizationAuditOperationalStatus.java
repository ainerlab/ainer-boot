package dev.ainer.module.workspace.workspace.application;

import java.time.Instant;

public record WorkspaceAuthorizationAuditOperationalStatus(
        long hot,
        long archived,
        long deniedInWindow,
        long ownerlessWorkspaces,
        Instant oldestHotAt) {
}
