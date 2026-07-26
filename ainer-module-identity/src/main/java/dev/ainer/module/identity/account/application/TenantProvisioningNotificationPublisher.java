package dev.ainer.module.identity.account.application;

public interface TenantProvisioningNotificationPublisher {

    void publish(TenantProvisioningNotificationDelivery delivery);
}
