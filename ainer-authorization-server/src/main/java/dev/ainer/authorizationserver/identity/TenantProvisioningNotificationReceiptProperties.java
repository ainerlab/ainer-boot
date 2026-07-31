package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.identity.provisioning-notification-receipts")
public class TenantProvisioningNotificationReceiptProperties {

    private final boolean enabled;
    private final List<String> gatewayClientIds;

    public TenantProvisioningNotificationReceiptProperties(boolean enabled, List<String> gatewayClientIds) {
        this.enabled = enabled;
        this.gatewayClientIds = gatewayClientIds == null
                ? new ArrayList<>()
                : new ArrayList<>(gatewayClientIds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getGatewayClientIds() {
        return new ArrayList<>(gatewayClientIds);
    }
}
