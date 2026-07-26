package dev.ainer.module.identity.account.application;

public record TenantProvisioningCancellationResult(
        TenantProvisioningRequest request,
        boolean cancelled) {
}
