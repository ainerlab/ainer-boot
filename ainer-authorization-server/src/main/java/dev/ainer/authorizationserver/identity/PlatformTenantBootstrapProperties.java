package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.platform.tenant-bootstrap")
public final class PlatformTenantBootstrapProperties {

    private final boolean enabled;
    private final String tenantCode;
    private final String tenantName;
    private final String username;
    private final String password;
    private final String displayName;

    public PlatformTenantBootstrapProperties(
            boolean enabled,
            String tenantCode,
            String tenantName,
            String username,
            String password,
            String displayName) {
        this.enabled = enabled;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public String getTenantName() {
        return tenantName;
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
