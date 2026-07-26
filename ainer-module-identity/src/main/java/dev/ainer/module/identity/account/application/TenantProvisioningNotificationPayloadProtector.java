package dev.ainer.module.identity.account.application;

public interface TenantProvisioningNotificationPayloadProtector {

    ProtectedTenantProvisioningNotification protect(
            TenantProvisioningNotification notification);

    TenantProvisioningNotification unprotect(
            ProtectedTenantProvisioningNotification protectedNotification);
}
