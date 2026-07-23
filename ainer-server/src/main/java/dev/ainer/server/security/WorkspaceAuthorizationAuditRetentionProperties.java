package dev.ainer.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ainer.workspace.authorization-audit-retention")
public class WorkspaceAuthorizationAuditRetentionProperties {

    private boolean enabled;
    private Duration hotRetention = Duration.ofDays(90);
    private Duration fixedDelay = Duration.ofMinutes(5);
    private Duration deniedWindow = Duration.ofMinutes(5);
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
