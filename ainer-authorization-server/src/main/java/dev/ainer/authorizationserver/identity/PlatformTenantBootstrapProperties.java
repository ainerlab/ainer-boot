package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.platform.tenant-bootstrap")
public final class PlatformTenantBootstrapProperties {

    private boolean enabled;
    private String tenantCode;
    private String tenantName;
    private String username;
    private String password;
    private String displayName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
