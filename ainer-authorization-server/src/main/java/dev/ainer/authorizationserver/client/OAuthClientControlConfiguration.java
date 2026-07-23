package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import dev.ainer.core.error.ErrorCodeContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthClientControlProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.client-control",
        name = "enabled",
        havingValue = "true")
public class OAuthClientControlConfiguration {

    static final String MANAGE_SCOPE =
            AinerAuthorizationServerConfiguration.CLIENT_CONTROL_MANAGE_SCOPE;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> RESERVED_SCOPES =
            Set.of(MANAGE_SCOPE, "token.introspect", "platform.metrics.read");

    @Bean
    OAuthClientControlSettings oauthClientControlSettings(OAuthClientControlProperties properties) {
        Set<String> operators = normalized(properties.getOperatorClientIds(), IDENTIFIER);
        if (operators.isEmpty()) {
            throw new IllegalStateException(
                    "Ainer OAuth client-control operator-client-ids must not be empty");
        }
        Set<String> scopes = normalized(properties.getAllowedScopes(), SCOPE);
        if (scopes.isEmpty()
                || scopes.stream().anyMatch(scope ->
                        RESERVED_SCOPES.contains(scope) || scope.endsWith(".all"))) {
            throw new IllegalStateException(
                    "Ainer OAuth client-control allowed-scopes contains an empty or reserved scope");
        }
        requireDuration(
                properties.getAccessTokenTtl(),
                Duration.ofSeconds(30),
                Duration.ofMinutes(15),
                "access-token-ttl");
        requireDuration(
                properties.getClientSecretTtl(),
                Duration.ofDays(1),
                Duration.ofDays(365),
                "client-secret-ttl");
        if (properties.getSecretBytes() < 32 || properties.getSecretBytes() > 64) {
            throw new IllegalStateException(
                    "Ainer OAuth client-control secret-bytes must be between 32 and 64");
        }
        return new OAuthClientControlSettings(
                operators,
                scopes,
                properties.getAccessTokenTtl(),
                properties.getClientSecretTtl(),
                properties.getSecretBytes());
    }

    @Bean
    SecureRandom oauthClientSecretSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock oauthClientControlClock() {
        return Clock.systemUTC();
    }

    @Bean
    ErrorCodeContributor oauthClientControlErrorCodes() {
        return () -> List.of(OAuthClientControlErrorCode.values());
    }

    private Set<String> normalized(List<String> values, Pattern pattern) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (value == null || !pattern.matcher(value).matches()) {
                throw new IllegalStateException(
                        "Ainer OAuth client-control identifier or scope is invalid");
            }
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }

    private void requireDuration(
            Duration value,
            Duration minimum,
            Duration maximum,
            String propertyName) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(
                    "Ainer OAuth client-control %s is outside the supported range"
                            .formatted(propertyName));
        }
    }
}
