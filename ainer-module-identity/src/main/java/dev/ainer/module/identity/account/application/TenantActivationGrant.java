package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantActivationGrant(
        UUID id,
        UUID provisioningRequestId,
        UUID tenantId,
        UUID subjectId,
        String secretHash,
        String status,
        int attemptCount,
        int maxAttempts,
        Instant createdAt,
        Instant expiresAt,
        Instant lastAttemptAt,
        Instant consumedAt) {

    public TenantActivationGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(secretHash, "secretHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (attemptCount < 0 || maxAttempts < 1 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException("Activation grant attempt count is invalid");
        }
    }
}
