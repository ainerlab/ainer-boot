package dev.ainer.authorizationserver.client;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties("ainer.security.authorization-server.browser-client-control")
public class OAuthBrowserClientControlProperties {

    private boolean enabled;
    private Set<String> operatorClientIds = Set.of();
    private Set<String> allowedScopes = Set.of();
    @Min(1)
    private int defaultAccessTokenMinutes = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Set<String> getOperatorClientIds() { return operatorClientIds; }
    public void setOperatorClientIds(Set<String> operatorClientIds) { this.operatorClientIds = operatorClientIds; }

    public Set<String> getAllowedScopes() { return allowedScopes; }
    public void setAllowedScopes(Set<String> allowedScopes) { this.allowedScopes = allowedScopes; }

    public int getDefaultAccessTokenMinutes() { return defaultAccessTokenMinutes; }
    public void setDefaultAccessTokenMinutes(int minutes) { this.defaultAccessTokenMinutes = minutes; }
}
