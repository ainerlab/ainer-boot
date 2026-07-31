package dev.ainer.authorizationserver.passkey;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.security.authorization-server.passkey.recovery")
public final class AinerPasskeyRecoveryProperties {

    private final boolean enabled;
    private final boolean selfServiceEnabled;
    @Positive
    private final Duration approvalTtl;

    public AinerPasskeyRecoveryProperties(boolean enabled, boolean selfServiceEnabled, Duration approvalTtl) {
        this.enabled = enabled;
        this.selfServiceEnabled = selfServiceEnabled;
        this.approvalTtl = approvalTtl != null ? approvalTtl : Duration.ofMinutes(15);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelfServiceEnabled() {
        return selfServiceEnabled;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }
}
