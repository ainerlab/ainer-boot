package dev.ainer.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkspaceAuthorizationAuditExportProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.workspace.authorization-audit-export",
        name = "enabled",
        havingValue = "true")
public class WorkspaceAuthorizationAuditExportConfiguration {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    @Bean
    WorkspaceAuthorizationAuditExportSettings workspaceAuthorizationAuditExportSettings(
            WorkspaceAuthorizationAuditExportProperties properties) {
        String subject = properties.getTrustedExporterSubject();
        if (subject == null || !IDENTIFIER.matcher(subject.trim()).matches()) {
            throw new IllegalStateException(
                    "Ainer workspace authorization-audit-export trusted-exporter-subject is required and invalid");
        }
        return new WorkspaceAuthorizationAuditExportSettings(subject.trim());
    }
}
