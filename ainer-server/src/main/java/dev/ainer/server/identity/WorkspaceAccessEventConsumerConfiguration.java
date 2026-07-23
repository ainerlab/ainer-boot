package dev.ainer.server.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkspaceAccessEventConsumerProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.workspace.access-event-consumer",
        name = "enabled",
        havingValue = "true")
public class WorkspaceAccessEventConsumerConfiguration {

    private static final Pattern SERVICE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Duration MAX_FUTURE_SKEW_LIMIT = Duration.ofDays(1);

    @Bean
    WorkspaceAccessEventConsumerSettings workspaceAccessEventConsumerSettings(
            WorkspaceAccessEventConsumerProperties properties) {
        String publisher = properties.getTrustedPublisherSubject();
        if (publisher == null || !SERVICE_IDENTIFIER.matcher(publisher.trim()).matches()) {
            throw new IllegalStateException(
                    "Ainer workspace access-event-consumer trusted-publisher-subject is required and invalid");
        }
        Duration skew = properties.getMaxFutureSkew();
        if (skew == null || skew.isNegative() || skew.isZero() || skew.compareTo(MAX_FUTURE_SKEW_LIMIT) > 0) {
            throw new IllegalStateException(
                    "Ainer workspace access-event-consumer max-future-skew must be between 0 and 1 day");
        }
        Duration propagationSlo = properties.getPropagationSlo();
        if (propagationSlo == null || !propagationSlo.isPositive()
                || propagationSlo.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalStateException(
                    "Ainer workspace access-event-consumer propagation-slo must be between 0 and 1 day");
        }
        return new WorkspaceAccessEventConsumerSettings(publisher.trim(), skew, propagationSlo);
    }
}
