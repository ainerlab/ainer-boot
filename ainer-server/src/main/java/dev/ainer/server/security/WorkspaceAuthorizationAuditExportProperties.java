package dev.ainer.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.workspace.authorization-audit-export")
public class WorkspaceAuthorizationAuditExportProperties {

    private boolean enabled;
    private String trustedExporterSubject;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTrustedExporterSubject() { return trustedExporterSubject; }
    public void setTrustedExporterSubject(String trustedExporterSubject) {
        this.trustedExporterSubject = trustedExporterSubject;
    }
}
