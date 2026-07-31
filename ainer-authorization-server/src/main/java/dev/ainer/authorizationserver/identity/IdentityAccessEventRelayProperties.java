package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ainer.identity.access-event-relay")
public class IdentityAccessEventRelayProperties {

    private final boolean enabled;
    private final String workspaceBaseUrl;
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

    public IdentityAccessEventRelayProperties(
            boolean enabled,
            String workspaceBaseUrl,
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
        this.workspaceBaseUrl = workspaceBaseUrl;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope != null ? scope : "identity.access-events.publish";
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

    public String getWorkspaceBaseUrl() {
        return workspaceBaseUrl;
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
