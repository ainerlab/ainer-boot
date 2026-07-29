package dev.ainer.authorizationserver.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("ainer.security.authorization-server.browser-client-control")
public class OAuthBrowserClientControlProperties {

    private boolean enabled;
    private Set<String> operatorClientIds = Set.of();
    private Set<String> allowedScopes = Set.of();
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
