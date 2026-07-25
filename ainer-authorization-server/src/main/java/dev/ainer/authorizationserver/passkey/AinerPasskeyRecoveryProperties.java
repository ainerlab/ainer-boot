package dev.ainer.authorizationserver.passkey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ainer.security.authorization-server.passkey.recovery")
public final class AinerPasskeyRecoveryProperties {

    private boolean enabled;
    private boolean selfServiceEnabled;
    private Duration approvalTtl = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSelfServiceEnabled() {
        return selfServiceEnabled;
    }

    public void setSelfServiceEnabled(boolean selfServiceEnabled) {
        this.selfServiceEnabled = selfServiceEnabled;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }

    public void setApprovalTtl(Duration approvalTtl) {
        this.approvalTtl = approvalTtl;
    }
}
