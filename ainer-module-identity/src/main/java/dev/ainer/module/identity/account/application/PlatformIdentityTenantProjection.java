package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityStatus;

import java.time.Instant;
import java.util.UUID;

public record PlatformIdentityTenantProjection(
        UUID id,
        String code,
        String name,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
