package dev.ainer.module.identity.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantMembership(
        UUID tenantId,
        UUID userId,
        TenantRole role,
        boolean defaultTenant,
        IdentityStatus status,
        Instant joinedAt,
        Instant updatedAt) {

    public TenantMembership {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("Membership update time cannot precede join time");
        }
    }
}
