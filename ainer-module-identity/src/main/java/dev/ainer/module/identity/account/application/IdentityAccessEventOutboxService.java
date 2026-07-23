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
public class IdentityAccessEventOutboxService {

    private final IdentityAccessEventOutboxRepository repository;

    public IdentityAccessEventOutboxService(IdentityAccessEventOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<IdentityAccessEventDelivery> claimBatch(
            String leaseOwner,
            Instant now,
            Duration leaseDuration,
            int maxAttempts,
            int limit) {
        requireText(leaseOwner, "lease owner");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (!leaseDuration.isPositive() || maxAttempts < 1 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Invalid access event claim settings");
        }
        return repository.claimBatch(
                leaseOwner, now, now.plus(leaseDuration), maxAttempts, limit);
    }

    @Transactional
    public void markPublished(UUID eventId, String leaseOwner, Instant publishedAt) {
        if (!repository.markPublished(
                Objects.requireNonNull(eventId, "eventId"),
                requireText(leaseOwner, "lease owner"),
                Objects.requireNonNull(publishedAt, "publishedAt"))) {
            throw new BusinessException(IdentityErrorCode.ACCESS_EVENT_LEASE_LOST);
        }
    }

    @Transactional
    public void markFailed(UUID eventId, String leaseOwner, Instant availableAt, String errorCode) {
        if (!repository.markFailed(
                Objects.requireNonNull(eventId, "eventId"),
                requireText(leaseOwner, "lease owner"),
                Objects.requireNonNull(availableAt, "availableAt"),
                requireText(errorCode, "error code"))) {
            throw new BusinessException(IdentityErrorCode.ACCESS_EVENT_LEASE_LOST);
        }
    }

    @Transactional(readOnly = true)
    public IdentityAccessEventOutboxStatus status(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Access event maximum attempts must be positive");
        }
        return repository.status(maxAttempts);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Access event " + name + " is required");
        }
        return value;
    }
}
