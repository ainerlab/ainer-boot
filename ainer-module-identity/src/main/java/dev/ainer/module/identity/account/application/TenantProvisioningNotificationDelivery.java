package dev.ainer.module.identity.account.application;

import java.util.Objects;
import java.util.UUID;

public record TenantProvisioningNotificationDelivery(
        UUID notificationId,
        int templateVersion,
        TenantProvisioningNotification notification) {

    public TenantProvisioningNotificationDelivery {
        Objects.requireNonNull(notificationId, "notificationId");
        Objects.requireNonNull(notification, "notification");
        if (templateVersion != 1) {
            throw new IllegalArgumentException(
                    "Tenant provisioning notification template version is unsupported");
        }
    }
}
