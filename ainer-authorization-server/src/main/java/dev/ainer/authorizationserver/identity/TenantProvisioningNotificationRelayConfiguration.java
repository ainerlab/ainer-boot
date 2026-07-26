package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.TenantProvisioningNotificationOutboxService;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPayloadProtector;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPublisher;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationRelay;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;
import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TenantProvisioningNotificationRelayProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.provisioning-notification-relay",
        name = "enabled",
        havingValue = "true")
public class TenantProvisioningNotificationRelayConfiguration {

    @Bean("tenantProvisioningNotificationServiceTokenProvider")
    ClientCredentialsServiceTokenProvider
            tenantProvisioningNotificationServiceTokenProvider(
                    TenantProvisioningNotificationRelayProperties properties) {
        return new ClientCredentialsServiceTokenProvider(
                requireUri(
                        properties.getTokenUri(),
                        properties.isAllowInsecureHttp(),
                        "token-uri"),
                properties.getClientId(),
                properties.getClientSecret(),
                Set.of(requireText(properties.getScope(), "scope")),
                properties.isAllowInsecureHttp());
    }

    @Bean
    TenantProvisioningNotificationPublisher tenantProvisioningNotificationPublisher(
            TenantProvisioningNotificationRelayProperties properties,
            @Qualifier("tenantProvisioningNotificationServiceTokenProvider")
                    ClientCredentialsServiceTokenProvider tokenProvider) {
        return new HttpTenantProvisioningNotificationPublisher(
                requireUri(
                        properties.getGatewayUri(),
                        properties.isAllowInsecureHttp(),
                        "gateway-uri"),
                tokenProvider);
    }

    @Bean
    TenantProvisioningNotificationRelay tenantProvisioningNotificationRelay(
            TenantProvisioningNotificationOutboxService outboxService,
            TenantProvisioningNotificationPayloadProtector protector,
            TenantProvisioningNotificationPublisher publisher,
            Clock clock) {
        return new TenantProvisioningNotificationRelay(
                outboxService, protector, publisher, clock);
    }

    @Bean
    TenantProvisioningNotificationRelayRunner
            tenantProvisioningNotificationRelayRunner(
                    TenantProvisioningNotificationRelay relay,
                    TenantProvisioningNotificationOutboxService outboxService,
                    TenantProvisioningNotificationRelayProperties properties,
                    Clock clock,
                    MeterRegistry meterRegistry) {
        validateSettings(properties);
        return new TenantProvisioningNotificationRelayRunner(
                relay, outboxService, properties, clock, meterRegistry);
    }

    void validateSettings(TenantProvisioningNotificationRelayProperties properties) {
        if (properties.getFixedDelay() == null
                || !properties.getFixedDelay().isPositive()
                || properties.getLeaseDuration() == null
                || !properties.getLeaseDuration().isPositive()
                || properties.getRetryDelay() == null
                || !properties.getRetryDelay().isPositive()
                || properties.getMaxAttempts() < 1
                || properties.getBatchSize() < 1
                || properties.getBatchSize() > 500) {
            throw new IllegalStateException(
                    "Ainer tenant provisioning notification relay settings are invalid");
        }
    }

    URI requireUri(String value, boolean allowInsecureHttp, String name) {
        try {
            URI uri = URI.create(requireText(value, name));
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (allowInsecureHttp
                            && "http".equalsIgnoreCase(uri.getScheme())
                            && isLoopbackHost(uri.getHost()));
            if (!validScheme
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ainer tenant provisioning notification relay "
                            + name + " is invalid",
                    exception);
        }
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Ainer tenant provisioning notification relay "
                            + name + " is required");
        }
        return value.trim();
    }
}
