package dev.ainer.authorizationserver.identity;

import java.util.Set;

public record TenantProvisioningNotificationReceiptSettings(
        Set<String> gatewayClientIds) {
}
