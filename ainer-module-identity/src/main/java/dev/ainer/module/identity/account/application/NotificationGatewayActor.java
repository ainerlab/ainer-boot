package dev.ainer.module.identity.account.application;

public record NotificationGatewayActor(
        String serviceId,
        String tenantId,
        String requestId) {
}
