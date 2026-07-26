package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningRequest(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        UUID ownerSubjectId,
        String ownerUsername,
        String ownerDisplayName,
        boolean ownerUserExists,
        String status,
        String idempotencyKey,
        String requestFingerprint,
        String requestedByServiceId,
        String requestId,
        String changeReference,
        Instant requestedAt,
        Instant expiresAt,
        Instant completedAt,
        long version) {

    public TenantProvisioningRequest expired(Instant completedAt) {
        return completed("EXPIRED", completedAt);
    }

    public TenantProvisioningRequest activated(Instant completedAt) {
        return completed("ACTIVATED", completedAt);
    }

    public TenantProvisioningRequest cancelled(Instant completedAt) {
        return completed("CANCELLED", completedAt);
    }

    private TenantProvisioningRequest completed(String completedStatus, Instant completedAt) {
        return new TenantProvisioningRequest(
                id,
                tenantId,
                tenantCode,
                tenantName,
                ownerSubjectId,
                ownerUsername,
                ownerDisplayName,
                ownerUserExists,
                completedStatus,
                idempotencyKey,
                requestFingerprint,
                requestedByServiceId,
                requestId,
                changeReference,
                requestedAt,
                expiresAt,
                completedAt,
                version + 1);
    }
}
