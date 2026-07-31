package dev.ainer.authorizationserver.identity;

import dev.ainer.security.AinerSecurityScopes;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ainer.identity.provisioning-notification-relay")
public class TenantProvisioningNotificationRelayProperties {

    private final boolean enabled;
    private final String gatewayUri;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final boolean allowInsecureHttp;
    private final Duration fixedDelay;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final int maxAttempts;
    private final int batchSize;

    public TenantProvisioningNotificationRelayProperties(
            boolean enabled,
            String gatewayUri,
            String tokenUri,
            String clientId,
            String clientSecret,
            String scope,
            boolean allowInsecureHttp,
            Duration fixedDelay,
            Duration leaseDuration,
            Duration retryDelay,
            Integer maxAttempts,
            Integer batchSize) {
        this.enabled = enabled;
        this.gatewayUri = gatewayUri;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope != null
                ? scope
                : AinerSecurityScopes.IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH;
        this.allowInsecureHttp = allowInsecureHttp;
        this.fixedDelay = fixedDelay != null ? fixedDelay : Duration.ofSeconds(5);
        this.leaseDuration = leaseDuration != null ? leaseDuration : Duration.ofSeconds(30);
        this.retryDelay = retryDelay != null ? retryDelay : Duration.ofSeconds(30);
        this.maxAttempts = maxAttempts != null ? maxAttempts : 10;
        this.batchSize = batchSize != null ? batchSize : 50;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getGatewayUri() {
        return gatewayUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public boolean isAllowInsecureHttp() {
        return allowInsecureHttp;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
