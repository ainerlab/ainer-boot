package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.identity.provisioning-notification-receipts")
public class TenantProvisioningNotificationReceiptProperties {

    private boolean enabled;
    private List<String> gatewayClientIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getGatewayClientIds() {
        return new ArrayList<>(gatewayClientIds);
    }

    public void setGatewayClientIds(List<String> gatewayClientIds) {
        this.gatewayClientIds = gatewayClientIds == null
                ? new ArrayList<>()
                : new ArrayList<>(gatewayClientIds);
    }
}
