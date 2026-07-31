package dev.ainer.server.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.workspace.authorization-audit-retention")
public class WorkspaceAuthorizationAuditRetentionProperties {

    private final boolean enabled;
    @Positive
    private final Duration hotRetention;
    @Positive
    private final Duration fixedDelay;
    @Positive
    private final Duration deniedWindow;
    @Min(1)
    private final int batchSize;

    public WorkspaceAuthorizationAuditRetentionProperties(
            boolean enabled, Duration hotRetention, Duration fixedDelay, Duration deniedWindow, Integer batchSize) {
        this.enabled = enabled;
        this.hotRetention = hotRetention != null ? hotRetention : Duration.ofDays(90);
        this.fixedDelay = fixedDelay != null ? fixedDelay : Duration.ofMinutes(5);
        this.deniedWindow = deniedWindow != null ? deniedWindow : Duration.ofMinutes(5);
        this.batchSize = batchSize != null ? batchSize : 500;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getHotRetention() {
        return hotRetention;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public Duration getDeniedWindow() {
        return deniedWindow;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
