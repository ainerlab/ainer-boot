package dev.ainer.authorizationserver.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityAccessEventRecoveryProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.access-event-recovery",
        name = "enabled",
        havingValue = "true")
public class IdentityAccessEventRecoveryConfiguration {

    @Bean
    IdentityAccessEventRecoverySettings identityAccessEventRecoverySettings(
            IdentityAccessEventRecoveryProperties properties) {
        Duration ttl = properties.getApprovalTtl();
        if (ttl == null || !ttl.isPositive() || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalStateException(
                    "Ainer identity access-event-recovery approval-ttl must be between 0 and 1 day");
        }
        if (properties.getMaxAttempts() < 1) {
            throw new IllegalStateException(
                    "Ainer identity access-event-recovery max-attempts must be positive");
        }
        return new IdentityAccessEventRecoverySettings(ttl, properties.getMaxAttempts());
    }
}
