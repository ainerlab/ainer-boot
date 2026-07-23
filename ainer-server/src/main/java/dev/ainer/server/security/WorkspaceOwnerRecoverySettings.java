package dev.ainer.server.security;

import java.time.Duration;

public record WorkspaceOwnerRecoverySettings(Duration approvalTtl) {
}
