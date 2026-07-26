package dev.ainer.module.identity.account.application;

import java.util.Optional;
import java.util.UUID;

public interface TenantProvisioningNotificationReceiptRepository {

    void acquireLocks(String gatewayClientId, String eventId, UUID notificationId);

    Optional<TenantProvisioningNotificationReceipt> findByGatewayEvent(
            String gatewayClientId,
            String eventId);

    Optional<TenantProvisioningNotificationReceipt> findByNotification(
            UUID notificationId);

    Optional<TenantProvisioningNotificationPublication> findPublicationForUpdate(
            UUID notificationId);

    UUID nextUuidV7();

    void insert(TenantProvisioningNotificationReceipt receipt);
}
