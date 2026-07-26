package dev.ainer.authorizationserver.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProvisioningNotificationReceiptProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.provisioning-notification-receipts",
        name = "enabled",
        havingValue = "true")
public class TenantProvisioningNotificationReceiptConfiguration {

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    @Bean
    TenantProvisioningNotificationReceiptSettings
            tenantProvisioningNotificationReceiptSettings(
                    TenantProvisioningNotificationReceiptProperties properties) {
        LinkedHashSet<String> gatewayClientIds = new LinkedHashSet<>();
        for (String clientId : properties.getGatewayClientIds()) {
            if (clientId == null || !IDENTIFIER.matcher(clientId).matches()) {
                throw new IllegalStateException(
                        "Ainer provisioning notification receipt gateway client id is invalid");
            }
            gatewayClientIds.add(clientId);
        }
        if (gatewayClientIds.isEmpty()) {
            throw new IllegalStateException(
                    "Ainer provisioning notification receipt gateway-client-ids "
                            + "must not be empty");
        }
        return new TenantProvisioningNotificationReceiptSettings(
                java.util.Set.copyOf(gatewayClientIds));
    }
}
