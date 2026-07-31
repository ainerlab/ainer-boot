package dev.ainer.authorizationserver.client;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties("ainer.security.authorization-server.browser-client-control")
public class OAuthBrowserClientControlProperties {

    private final boolean enabled;
    private final Set<String> operatorClientIds;
    private final Set<String> allowedScopes;
    @Min(1)
    private final int defaultAccessTokenMinutes;

    public OAuthBrowserClientControlProperties(
            boolean enabled, Set<String> operatorClientIds, Set<String> allowedScopes, Integer defaultAccessTokenMinutes) {
        this.enabled = enabled;
        this.operatorClientIds = operatorClientIds != null ? operatorClientIds : Set.of();
        this.allowedScopes = allowedScopes != null ? allowedScopes : Set.of();
        this.defaultAccessTokenMinutes = defaultAccessTokenMinutes != null ? defaultAccessTokenMinutes : 5;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getOperatorClientIds() {
        return operatorClientIds;
    }

    public Set<String> getAllowedScopes() {
        return allowedScopes;
    }

    public int getDefaultAccessTokenMinutes() {
        return defaultAccessTokenMinutes;
    }
}
