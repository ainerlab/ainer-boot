package dev.ainer.server.security;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("ainer.workspace.owner-recovery")
public class WorkspaceOwnerRecoveryProperties {

    private final boolean enabled;
    @Positive
    private final Duration approvalTtl;

    public WorkspaceOwnerRecoveryProperties(boolean enabled, Duration approvalTtl) {
        this.enabled = enabled;
        this.approvalTtl = approvalTtl != null ? approvalTtl : Duration.ofMinutes(15);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }
}
