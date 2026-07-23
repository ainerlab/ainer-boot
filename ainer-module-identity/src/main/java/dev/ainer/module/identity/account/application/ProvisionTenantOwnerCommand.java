package dev.ainer.module.identity.account.application;

public record ProvisionTenantOwnerCommand(
        String tenantCode,
        String tenantName,
        String username,
        String rawPassword,
        String displayName) {
}
