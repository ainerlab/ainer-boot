package dev.ainer.authorizationserver.identity;

import java.time.Duration;

public record IdentityAccessEventRecoverySettings(Duration approvalTtl, int maxAttempts) {
}
