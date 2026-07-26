package dev.ainer.module.identity.account.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.UUID;

public interface TenantProvisioningNotificationReceiptMapper {

    int acquireReceiptLock(@Param("lockKey") String lockKey);

    TenantProvisioningNotificationReceiptRow selectByGatewayEvent(
            @Param("gatewayClientId") String gatewayClientId,
            @Param("gatewayEventId") String gatewayEventId);

    TenantProvisioningNotificationReceiptRow selectByNotification(
            @Param("notificationId") UUID notificationId);

    TenantProvisioningNotificationPublicationRow selectPublicationForUpdate(
            @Param("notificationId") UUID notificationId);

    UUID selectUuidV7();

    int insertReceipt(TenantProvisioningNotificationReceiptRow receipt);
}
