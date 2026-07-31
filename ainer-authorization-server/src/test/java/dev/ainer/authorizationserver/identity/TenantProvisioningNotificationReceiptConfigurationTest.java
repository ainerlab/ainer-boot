package dev.ainer.authorizationserver.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisioningNotificationReceiptConfigurationTest {

    private final TenantProvisioningNotificationReceiptConfiguration configuration =
            new TenantProvisioningNotificationReceiptConfiguration();

    @Test
    void acceptsDistinctExactGatewayClientIds() {
        TenantProvisioningNotificationReceiptProperties properties =
                new TenantProvisioningNotificationReceiptProperties(false, List.of(
                        "notification-gateway",
                        "notification-gateway"));

        TenantProvisioningNotificationReceiptSettings settings =
                configuration.tenantProvisioningNotificationReceiptSettings(
                        properties);

        assertThat(settings.gatewayClientIds())
                .containsExactly("notification-gateway");
    }

    @Test
    void rejectsMissingOrUnsafeGatewayClientIds() {
        assertThatThrownBy(() ->
                configuration.tenantProvisioningNotificationReceiptSettings(
                        new TenantProvisioningNotificationReceiptProperties(false, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway-client-ids");

        TenantProvisioningNotificationReceiptProperties unsafe =
                new TenantProvisioningNotificationReceiptProperties(false, List.of("unsafe gateway"));
        assertThatThrownBy(() ->
                configuration.tenantProvisioningNotificationReceiptSettings(
                        unsafe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway client id");
    }
}
