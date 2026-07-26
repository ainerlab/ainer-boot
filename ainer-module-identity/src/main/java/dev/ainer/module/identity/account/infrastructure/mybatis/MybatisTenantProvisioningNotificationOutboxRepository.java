package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.ProtectedTenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxEntry;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxRepository;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxStatus;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public class MybatisTenantProvisioningNotificationOutboxRepository
        implements TenantProvisioningNotificationOutboxRepository {

    private final TenantProvisioningNotificationOutboxMapper mapper;

    public MybatisTenantProvisioningNotificationOutboxRepository(
            TenantProvisioningNotificationOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TenantProvisioningNotificationOutboxEntry> claimBatch(
            String leaseOwner,
            Instant now,
            Instant leaseUntil,
            int maxAttempts,
            int limit) {
        return mapper.claimBatch(leaseOwner, now, leaseUntil, maxAttempts, limit)
                .stream()
                .map(this::toEntry)
                .sorted(Comparator
                        .comparing(TenantProvisioningNotificationOutboxEntry::createdAt)
                        .thenComparing(TenantProvisioningNotificationOutboxEntry::id))
                .toList();
    }

    @Override
    public boolean markPublished(
            UUID notificationId,
            String leaseOwner,
            Instant publishedAt) {
        return mapper.markPublished(notificationId, leaseOwner, publishedAt) == 1;
    }

    @Override
    public boolean markFailed(
            UUID notificationId,
            String leaseOwner,
            Instant availableAt,
            String errorCode) {
        return mapper.markFailed(
                notificationId, leaseOwner, availableAt, errorCode) == 1;
    }

    @Override
    public TenantProvisioningNotificationOutboxStatus status(int maxAttempts) {
        TenantProvisioningNotificationOutboxStatusRow row =
                mapper.selectStatus(maxAttempts);
        return new TenantProvisioningNotificationOutboxStatus(
                row.getPending(),
                row.getFailed(),
                row.getExhausted(),
                row.getPublished(),
                row.getCancelled(),
                row.getOldestReadyAt());
    }

    private TenantProvisioningNotificationOutboxEntry toEntry(
            TenantProvisioningNotificationOutboxRow row) {
        return new TenantProvisioningNotificationOutboxEntry(
                row.getId(),
                row.getProvisioningRequestId(),
                row.getTenantId(),
                row.getSubjectId(),
                TenantProvisioningNotificationType.valueOf(row.getNotificationType()),
                row.getTemplateVersion(),
                new ProtectedTenantProvisioningNotification(
                        row.getPayloadKeyVersion(), row.getProtectedPayload()),
                row.getAttemptCount(),
                row.getCreatedAt());
    }
}
