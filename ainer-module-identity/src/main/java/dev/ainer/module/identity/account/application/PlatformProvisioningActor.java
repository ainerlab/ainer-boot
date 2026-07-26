package dev.ainer.module.identity.account.application;

public record PlatformProvisioningActor(
        String serviceId,
        String tenantId,
        String requestId) {
}
