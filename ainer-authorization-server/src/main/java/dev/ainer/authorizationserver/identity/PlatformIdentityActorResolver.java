package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.PlatformProvisioningActor;
import dev.ainer.security.service.AuthenticatedService;
import dev.ainer.security.service.JwtAuthenticatedServiceFactory;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "ainer.identity.platform-control",
        name = "enabled",
        havingValue = "true")
public class PlatformIdentityActorResolver {

    private final PlatformIdentityControlSettings settings;

    public PlatformIdentityActorResolver(PlatformIdentityControlSettings settings) {
        this.settings = settings;
    }

    public PlatformProvisioningActor require(
            Authentication authentication,
            HttpServletRequest request,
            String... authorities) {
        AuthenticatedService authenticated = JwtAuthenticatedServiceFactory.from(authentication);
        for (String authority : authorities) {
            authenticated.requireAuthority(authority);
        }
        if (authenticated.tenantId() != null
                || !settings.operatorClientIds().contains(authenticated.serviceId())) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return new PlatformProvisioningActor(
                authenticated.serviceId(),
                authenticated.tenantId(),
                RequestIds.currentOrCreate(request));
    }
}
