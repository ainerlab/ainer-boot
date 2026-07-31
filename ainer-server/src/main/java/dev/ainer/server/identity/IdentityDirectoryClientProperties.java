package dev.ainer.server.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.identity.directory-client")
public class IdentityDirectoryClientProperties {

    private final boolean enabled;
    private final String baseUrl;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final boolean allowInsecureHttp;

    public IdentityDirectoryClientProperties(
            boolean enabled,
            String baseUrl,
            String tokenUri,
            String clientId,
            String clientSecret,
            String scope,
            boolean allowInsecureHttp) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope != null ? scope : "identity.directory.read.all";
        this.allowInsecureHttp = allowInsecureHttp;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
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
}
