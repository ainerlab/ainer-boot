package dev.ainer.module.identity.account.application;

public record TenantProvisioningResult(
        TenantProvisioningRequest request,
        boolean created) {
}
