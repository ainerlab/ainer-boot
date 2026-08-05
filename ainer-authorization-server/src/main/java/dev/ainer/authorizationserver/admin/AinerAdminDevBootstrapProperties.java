package dev.ainer.authorizationserver.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.admin.dev-bootstrap")
public final class AinerAdminDevBootstrapProperties {

    private final boolean enabled;
    private final String ownerUsername;
    private final String ownerPassword;
    private final String ownerDisplayName;
    private final String memberUsername;
    private final String memberPassword;
    private final String memberDisplayName;

    public AinerAdminDevBootstrapProperties(
            boolean enabled,
            String ownerUsername,
            String ownerPassword,
            String ownerDisplayName,
            String memberUsername,
            String memberPassword,
            String memberDisplayName) {
        this.enabled = enabled;
        this.ownerUsername = ownerUsername;
        this.ownerPassword = ownerPassword;
        this.ownerDisplayName = ownerDisplayName;
        this.memberUsername = memberUsername;
        this.memberPassword = memberPassword;
        this.memberDisplayName = memberDisplayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getOwnerPassword() {
        return ownerPassword;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public String getMemberUsername() {
        return memberUsername;
    }

    public String getMemberPassword() {
        return memberPassword;
    }

    public String getMemberDisplayName() {
        return memberDisplayName;
    }
}
