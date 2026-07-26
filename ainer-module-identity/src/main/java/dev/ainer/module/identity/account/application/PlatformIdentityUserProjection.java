package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityStatus;

import java.time.Instant;
import java.util.UUID;

public record PlatformIdentityUserProjection(
        UUID subjectId,
        String username,
        String displayName,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
