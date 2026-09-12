package dev.ainer.server.security;

import dev.ainer.authorization.application.AuthorizationDecisionAuditLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * 决策审计保留任务的条件装配。默认关闭（{@code ainer.authorization.decision-audit-retention.enabled=false}），
 * 首次上线必须先按 {@code docs/operations.md} 在接近真实规模的库上验证批次、锁等待与 WAL。
 *
 * <p>全局 {@code @EnableScheduling} 由 {@code ainer-spring} 的
 * {@code AinerSchedulingAutoConfiguration} 提供（默认生效、与任何业务开关无关），本类只保留自己的
 * 业务开关——把调度能力挂在业务开关上会让默认关闭时进程内所有 {@code @Scheduled} 静默失效
 * （2026-09-11 的回归，见 {@code docs/project-status.md}）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthorizationDecisionAuditRetentionProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.authorization.decision-audit-retention",
        name = "enabled",
        havingValue = "true")
public class AuthorizationDecisionAuditRetentionConfiguration {

    @Bean
    AuthorizationDecisionAuditRetentionRunner authorizationDecisionAuditRetentionRunner(
            AuthorizationDecisionAuditLifecycleService lifecycleService,
            AuthorizationDecisionAuditRetentionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        validate(properties);
        return new AuthorizationDecisionAuditRetentionRunner(
                lifecycleService, properties, clock, meterRegistry);
    }

    private void validate(AuthorizationDecisionAuditRetentionProperties properties) {
        if (!positive(properties.getHotRetention())
                || !positive(properties.getFixedDelay())
                || !positive(properties.getInitialDelay())
                || !positive(properties.getOldestHotWarnWindow())
                || properties.getBatchSize() < 1
                || properties.getBatchSize() > 5000
                || properties.getMaxBatchesPerCycle() < 1
                || properties.getMaxBatchesPerCycle() > 1000) {
            throw new IllegalStateException(
                    "Ainer authorization decision audit retention settings are invalid");
        }
        if (properties.getOldestHotWarnWindow().compareTo(properties.getHotRetention()) <= 0) {
            throw new IllegalStateException(
                    "Ainer authorization decision audit oldest-hot-warn-window must be longer than hot-retention");
        }
    }

    private boolean positive(Duration duration) {
        return duration != null && duration.isPositive();
    }
}
