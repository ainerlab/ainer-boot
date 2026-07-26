package dev.ainer.module.identity.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantProvisioningNotificationOutboxEntry(
        UUID id,
        UUID provisioningRequestId,
        UUID tenantId,
        UUID subjectId,
        TenantProvisioningNotificationType type,
        int templateVersion,
        ProtectedTenantProvisioningNotification protectedNotification,
        int attemptCount,
        Instant createdAt) {

    public TenantProvisioningNotificationOutboxEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provisioningRequestId, "provisioningRequestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(protectedNotification, "protectedNotification");
        Objects.requireNonNull(createdAt, "createdAt");
        if (templateVersion != 1 || attemptCount < 0) {
            throw new IllegalArgumentException("Notification outbox metadata is invalid");
        }
    }
}
