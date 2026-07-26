package dev.ainer.module.identity.account.infrastructure.mybatis;

import dev.ainer.module.identity.account.application.TenantProvisioningNotificationDeliveryStatus;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublication;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceipt;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationReceiptRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisTenantProvisioningNotificationReceiptRepository
        implements TenantProvisioningNotificationReceiptRepository {

    private final TenantProvisioningNotificationReceiptMapper mapper;

    public MybatisTenantProvisioningNotificationReceiptRepository(
            TenantProvisioningNotificationReceiptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void acquireLocks(
            String gatewayClientId,
            String eventId,
            UUID notificationId) {
        mapper.acquireReceiptLock(
                "identity:notification-receipt:event:"
                        + gatewayClientId + '\u001f' + eventId);
        mapper.acquireReceiptLock(
                "identity:notification-receipt:notification:" + notificationId);
    }

    @Override
    public Optional<TenantProvisioningNotificationReceipt> findByGatewayEvent(
            String gatewayClientId,
            String eventId) {
        return Optional.ofNullable(
                        mapper.selectByGatewayEvent(gatewayClientId, eventId))
                .map(this::toReceipt);
    }

    @Override
    public Optional<TenantProvisioningNotificationReceipt> findByNotification(
            UUID notificationId) {
        return Optional.ofNullable(mapper.selectByNotification(notificationId))
                .map(this::toReceipt);
    }

    @Override
    public Optional<TenantProvisioningNotificationPublication>
            findPublicationForUpdate(UUID notificationId) {
        return Optional.ofNullable(mapper.selectPublicationForUpdate(notificationId))
                .map(row -> new TenantProvisioningNotificationPublication(
                        row.getNotificationId(),
                        row.getPublicationStatus(),
                        row.getPublishedAt()));
    }

    @Override
    public UUID nextUuidV7() {
        return mapper.selectUuidV7();
    }

    @Override
    public void insert(TenantProvisioningNotificationReceipt receipt) {
        TenantProvisioningNotificationReceiptRow row =
                new TenantProvisioningNotificationReceiptRow();
        row.setId(receipt.id());
        row.setNotificationId(receipt.notificationId());
        row.setGatewayClientId(receipt.gatewayClientId());
        row.setGatewayEventId(receipt.gatewayEventId());
        row.setDeliveryStatus(receipt.status().name());
        row.setFailureCode(receipt.failureCode());
        row.setOccurredAt(receipt.occurredAt());
        row.setReceivedAt(receipt.receivedAt());
        row.setRequestId(receipt.requestId());
        if (mapper.insertReceipt(row) != 1) {
            throw new IllegalStateException(
                    "Notification delivery receipt insert did not affect exactly one row");
        }
    }

    private TenantProvisioningNotificationReceipt toReceipt(
            TenantProvisioningNotificationReceiptRow row) {
        return new TenantProvisioningNotificationReceipt(
                row.getId(),
                row.getNotificationId(),
                row.getGatewayClientId(),
                row.getGatewayEventId(),
                TenantProvisioningNotificationDeliveryStatus.valueOf(
                        row.getDeliveryStatus()),
                row.getFailureCode(),
                row.getOccurredAt(),
                row.getReceivedAt(),
                row.getRequestId());
    }
}
