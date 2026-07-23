package dev.ainer.server.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkspaceOwnerRecoveryProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.workspace.owner-recovery",
        name = "enabled",
        havingValue = "true")
public class WorkspaceOwnerRecoveryConfiguration {

    @Bean
    WorkspaceOwnerRecoverySettings workspaceOwnerRecoverySettings(
            WorkspaceOwnerRecoveryProperties properties) {
        Duration ttl = properties.getApprovalTtl();
        if (ttl == null || !ttl.isPositive() || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalStateException(
                    "Ainer workspace owner-recovery approval-ttl must be between 0 and 1 day");
        }
        return new WorkspaceOwnerRecoverySettings(ttl);
    }
}
