package dev.ainer.module.identity.account.application;

public record TenantProvisioningNotificationRelayResult(
        int claimed,
        int published,
        int failed) {
}
