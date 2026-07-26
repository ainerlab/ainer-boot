package dev.ainer.module.identity.account.application;

public record TenantProvisioningNotificationReceiptResult(
        TenantProvisioningNotificationReceipt receipt,
        boolean created) {
}
