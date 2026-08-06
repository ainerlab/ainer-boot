package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.platform.tenant-bootstrap")
public final class PlatformTenantBootstrapProperties {

    private final boolean enabled;
    private final String username;
    private final String password;
    private final String displayName;

    public PlatformTenantBootstrapProperties(
            boolean enabled,
            String username,
            String password,
            String displayName) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }
}
