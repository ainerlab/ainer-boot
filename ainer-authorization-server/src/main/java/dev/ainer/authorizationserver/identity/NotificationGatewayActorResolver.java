package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.NotificationGatewayActor;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "ainer.identity.provisioning-notification-receipts",
        name = "enabled",
        havingValue = "true")
public class NotificationGatewayActorResolver {

    private final TenantProvisioningNotificationReceiptSettings settings;

    public NotificationGatewayActorResolver(
            TenantProvisioningNotificationReceiptSettings settings) {
        this.settings = settings;
    }

    public NotificationGatewayActor require(
            Authentication authentication,
            HttpServletRequest request) {
        AuthenticatedService authenticated =
                JwtAuthenticatedServiceFactory.from(authentication);
        authenticated.requireAuthority(
                "SCOPE_"
                        + AinerSecurityScopes
                                .IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_WRITE);
        if (authenticated.tenantId() != null
                || !settings.gatewayClientIds().contains(
                        authenticated.serviceId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return new NotificationGatewayActor(
                authenticated.serviceId(),
                authenticated.tenantId(),
                RequestIds.currentOrCreate(request));
    }
}
