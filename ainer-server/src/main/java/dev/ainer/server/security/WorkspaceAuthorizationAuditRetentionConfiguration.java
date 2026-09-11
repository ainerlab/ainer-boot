package dev.ainer.server.security;

import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * 审计保留任务的条件装配。全局 {@code @EnableScheduling} 已移到
 * {@code AinerSchedulingAutoConfiguration}（framework 层，默认生效）；本类只保留
 * 自己的业务开关 {@code ainer.workspace.authorization-audit-retention.enabled}。
 *
 * <p>2026-09-11 修复：此前 {@code @EnableScheduling} 挂在本类的 {@code @ConditionalOnProperty}
 * 之下，默认关闭时整个进程的调度器都不注册，通知投递引擎等 {@code @Scheduled} 组件静默失效。
 */
@Configuration(proxyBeanMethods = false)
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
