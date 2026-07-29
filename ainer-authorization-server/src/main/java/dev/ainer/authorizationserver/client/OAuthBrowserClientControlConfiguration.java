package dev.ainer.authorizationserver.client;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.browser-client-control",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(OAuthBrowserClientControlProperties.class)
public class OAuthBrowserClientControlConfiguration {

    private static final Set<String> RESERVED_SCOPES = Set.of(
            AinerAuthorizationServerConfiguration.CLIENT_CONTROL_MANAGE_SCOPE,
            AinerAuthorizationServerConfiguration.BROWSER_CLIENT_CONTROL_MANAGE_SCOPE,
            AinerAuthorizationServerConfiguration.INTROSPECTION_CLIENT_SCOPE,
            "platform.metrics.read");

    private final OAuthBrowserClientControlProperties properties;

    public OAuthBrowserClientControlConfiguration(OAuthBrowserClientControlProperties properties) {
        this.properties = properties;
        validate();
    }

    public Set<String> operatorClientIds() {
        return properties.getOperatorClientIds();
    }

    public Set<String> allowedScopes() {
        return properties.getAllowedScopes();
    }

    public int defaultAccessTokenMinutes() {
        return properties.getDefaultAccessTokenMinutes();
    }

    public void validateRequestedScopes(Set<String> scopes) {
        Objects.requireNonNull(scopes, "scopes");
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                throw new IllegalArgumentException("scope must not be blank");
            }
            if (scope.endsWith(".all") || RESERVED_SCOPES.contains(scope)) {
                throw new IllegalArgumentException("scope is reserved or forbidden: " + scope);
            }
            if (!properties.getAllowedScopes().contains(scope)) {
                throw new IllegalArgumentException("scope is not in the allowed list: " + scope);
            }
        }
    }

    private void validate() {
        if (properties.getOperatorClientIds() == null || properties.getOperatorClientIds().isEmpty()) {
            throw new IllegalStateException("Browser client control operator client IDs must not be empty");
        }
        for (String id : properties.getOperatorClientIds()) {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Browser client control operator client ID must not be blank");
            }
        }
    }
}
