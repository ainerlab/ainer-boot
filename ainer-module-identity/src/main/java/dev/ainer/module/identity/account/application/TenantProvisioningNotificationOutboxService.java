package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TenantProvisioningNotificationOutboxService {

    private final TenantProvisioningNotificationOutboxRepository repository;

    public TenantProvisioningNotificationOutboxService(
            TenantProvisioningNotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<TenantProvisioningNotificationOutboxEntry> claimBatch(
            String leaseOwner,
            Instant now,
            Duration leaseDuration,
            int maxAttempts,
            int limit) {
        requireText(leaseOwner, "lease owner");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (!leaseDuration.isPositive() || maxAttempts < 1 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Invalid notification claim settings");
        }
        return repository.claimBatch(
                leaseOwner, now, now.plus(leaseDuration), maxAttempts, limit);
    }

    @Transactional
    public void markPublished(
            UUID notificationId,
            String leaseOwner,
            Instant publishedAt) {
        if (!repository.markPublished(
                Objects.requireNonNull(notificationId, "notificationId"),
                requireText(leaseOwner, "lease owner"),
                Objects.requireNonNull(publishedAt, "publishedAt"))) {
            throw new BusinessException(IdentityErrorCode.NOTIFICATION_LEASE_LOST);
        }
    }

    @Transactional
    public void markFailed(
            UUID notificationId,
            String leaseOwner,
            Instant availableAt,
            String errorCode) {
        if (!repository.markFailed(
                Objects.requireNonNull(notificationId, "notificationId"),
                requireText(leaseOwner, "lease owner"),
                Objects.requireNonNull(availableAt, "availableAt"),
                requireText(errorCode, "error code"))) {
            throw new BusinessException(IdentityErrorCode.NOTIFICATION_LEASE_LOST);
        }
    }

    @Transactional(readOnly = true)
    public TenantProvisioningNotificationOutboxStatus status(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Notification maximum attempts must be positive");
        }
        return repository.status(maxAttempts);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification " + name + " is required");
        }
        return value;
    }
}
