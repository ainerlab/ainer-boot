package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WorkspaceAuthorizationAuditRetentionProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.workspace.authorization-audit-retention",
        name = "enabled",
        havingValue = "true")
public class WorkspaceAuthorizationAuditRetentionConfiguration {

    @Bean
    WorkspaceAuthorizationAuditRetentionRunner workspaceAuthorizationAuditRetentionRunner(
            WorkspaceAuthorizationAuditLifecycleService lifecycleService,
            WorkspaceAuthorizationAuditRetentionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        validate(properties);
        return new WorkspaceAuthorizationAuditRetentionRunner(
                lifecycleService, properties, clock, meterRegistry);
    }

    private void validate(WorkspaceAuthorizationAuditRetentionProperties properties) {
        if (!positive(properties.getHotRetention())
                || !positive(properties.getFixedDelay())
                || !positive(properties.getDeniedWindow())
                || properties.getBatchSize() < 1
                || properties.getBatchSize() > 5000) {
            throw new IllegalStateException("Ainer workspace authorization audit retention settings are invalid");
        }
    }

    private boolean positive(Duration duration) {
        return duration != null && duration.isPositive();
    }
}
