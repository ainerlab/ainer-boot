package dev.ainer.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.workspace.authorization-audit-export")
public class WorkspaceAuthorizationAuditExportProperties {

    private final boolean enabled;
    private final String trustedExporterSubject;

    public WorkspaceAuthorizationAuditExportProperties(boolean enabled, String trustedExporterSubject) {
        this.enabled = enabled;
        this.trustedExporterSubject = trustedExporterSubject;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTrustedExporterSubject() {
        return trustedExporterSubject;
    }
}
