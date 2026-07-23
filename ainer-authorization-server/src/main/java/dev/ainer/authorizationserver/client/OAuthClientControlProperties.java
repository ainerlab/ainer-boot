package dev.ainer.authorizationserver.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.security.authorization-server.client-control")
public class OAuthClientControlProperties {

    private boolean enabled;
    private List<String> operatorClientIds = new ArrayList<>();
    private List<String> allowedScopes = new ArrayList<>(List.of("ai.invoke"));
    private Duration accessTokenTtl = Duration.ofMinutes(5);
    private Duration clientSecretTtl = Duration.ofDays(90);
    private int secretBytes = 32;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getOperatorClientIds() {
        return operatorClientIds;
    }

    public void setOperatorClientIds(List<String> operatorClientIds) {
        this.operatorClientIds = new ArrayList<>(operatorClientIds);
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = new ArrayList<>(allowedScopes);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getClientSecretTtl() {
        return clientSecretTtl;
    }

    public void setClientSecretTtl(Duration clientSecretTtl) {
        this.clientSecretTtl = clientSecretTtl;
    }

    public int getSecretBytes() {
        return secretBytes;
    }

    public void setSecretBytes(int secretBytes) {
        this.secretBytes = secretBytes;
    }
}
