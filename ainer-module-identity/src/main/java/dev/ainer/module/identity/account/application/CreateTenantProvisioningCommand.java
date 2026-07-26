package dev.ainer.module.identity.account.application;

public record CreateTenantProvisioningCommand(
        String tenantCode,
        String tenantName,
        String ownerUsername,
        String ownerDisplayName,
        String deliveryChannel,
        String deliveryAddress,
        String idempotencyKey,
        String changeReference) {
}
