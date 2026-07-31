package dev.ainer.server.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.workspace.authorization-audit-retention")
public class WorkspaceAuthorizationAuditRetentionProperties {

    private boolean enabled;
    @Positive
    private Duration hotRetention = Duration.ofDays(90);
    @Positive
    private Duration fixedDelay = Duration.ofMinutes(5);
    @Positive
    private Duration deniedWindow = Duration.ofMinutes(5);
    @Min(1)
    private int batchSize = 500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getHotRetention() { return hotRetention; }
    public void setHotRetention(Duration hotRetention) { this.hotRetention = hotRetention; }
    public Duration getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
    public Duration getDeniedWindow() { return deniedWindow; }
    public void setDeniedWindow(Duration deniedWindow) { this.deniedWindow = deniedWindow; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
