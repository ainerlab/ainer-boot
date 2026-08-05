package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccessEventOutboxService;
import dev.ainer.module.identity.account.application.IdentityAccessEventPublisher;
import dev.ainer.module.identity.account.application.IdentityAccessEventRelay;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(IdentityAccessEventRelayProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.access-event-relay",
        name = "enabled",
        havingValue = "true")
public class IdentityAccessEventRelayConfiguration {

    @Bean("identityAccessEventServiceTokenProvider")
    ClientCredentialsServiceTokenProvider identityAccessEventServiceTokenProvider(
            IdentityAccessEventRelayProperties properties) {
        return new ClientCredentialsServiceTokenProvider(
                requireUri(properties.getTokenUri(), properties.isAllowInsecureHttp(), "token-uri"),
                properties.getClientId(),
                properties.getClientSecret(),
                Set.of(requireText(properties.getScope(), "scope")),
                properties.isAllowInsecureHttp());
    }

    @Bean
    IdentityAccessEventPublisher identityAccessEventPublisher(
            IdentityAccessEventRelayProperties properties,
            @Qualifier("identityAccessEventServiceTokenProvider")
                    ClientCredentialsServiceTokenProvider tokenProvider,
            RestClient.Builder restClientBuilder) {
        URI workspaceBaseUrl = requireUri(
                properties.getWorkspaceBaseUrl(),
                properties.isAllowInsecureHttp(),
                "workspace-base-url");
        RestClient restClient = restClientBuilder
                .baseUrl(withoutTrailingSlash(workspaceBaseUrl.toString()))
                .build();
        return new HttpIdentityAccessEventPublisher(restClient, tokenProvider);
    }

    @Bean
    IdentityAccessEventRelay identityAccessEventRelay(
            IdentityAccessEventOutboxService outboxService,
            IdentityAccessEventPublisher publisher,
            Clock clock) {
        return new IdentityAccessEventRelay(outboxService, publisher, clock);
    }

    @Bean
    IdentityAccessEventRelayRunner identityAccessEventRelayRunner(
            IdentityAccessEventRelay relay,
            IdentityAccessEventOutboxService outboxService,
            IdentityAccessEventRelayProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        validateSettings(properties);
        return new IdentityAccessEventRelayRunner(
                relay, outboxService, properties, clock, meterRegistry);
    }

    private void validateSettings(IdentityAccessEventRelayProperties properties) {
        if (properties.getFixedDelay() == null || !properties.getFixedDelay().isPositive()
                || properties.getLeaseDuration() == null || !properties.getLeaseDuration().isPositive()
                || properties.getRetryDelay() == null || !properties.getRetryDelay().isPositive()
                || properties.getMaxAttempts() < 1
                || properties.getBatchSize() < 1 || properties.getBatchSize() > 500) {
            throw new IllegalStateException("Ainer identity access event relay settings are invalid");
        }
    }

    private URI requireUri(String value, boolean allowInsecureHttp, String name) {
        try {
            URI uri = URI.create(requireText(value, name));
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme()));
            if (!validScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ainer identity access event relay " + name + " is invalid", exception);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Ainer identity access event relay " + name + " is required");
        }
        return value.trim();
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
