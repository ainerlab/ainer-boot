package dev.ainer.authorizationserver.identity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.identity.access-event-recovery")
public class IdentityAccessEventRecoveryProperties {

    private final boolean enabled;
    @Positive
    private final Duration approvalTtl;
    @Min(1)
    private final int maxAttempts;

    public IdentityAccessEventRecoveryProperties(boolean enabled, Duration approvalTtl, Integer maxAttempts) {
        this.enabled = enabled;
        this.approvalTtl = approvalTtl != null ? approvalTtl : Duration.ofMinutes(15);
        this.maxAttempts = maxAttempts != null ? maxAttempts : 10;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
