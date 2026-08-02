package dev.ainer.authorizationserver.client;

import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties("ainer.security.authorization-server.client-control")
public class OAuthClientControlProperties {

    private final boolean enabled;
    private final List<String> operatorClientIds;
    private final List<String> allowedScopes;
    @DurationMin(nanos = 1)
    private final Duration accessTokenTtl;
    @DurationMin(nanos = 1)
    private final Duration clientSecretTtl;
    @Min(1)
    private final int secretBytes;

    public OAuthClientControlProperties(
            boolean enabled,
            List<String> operatorClientIds,
            List<String> allowedScopes,
            Duration accessTokenTtl,
            Duration clientSecretTtl,
            Integer secretBytes) {
        this.enabled = enabled;
        this.operatorClientIds = operatorClientIds != null
                ? new ArrayList<>(operatorClientIds)
                : new ArrayList<>();
        this.allowedScopes = allowedScopes != null
                ? new ArrayList<>(allowedScopes)
                : new ArrayList<>(List.of("ai.invoke"));
        this.accessTokenTtl = accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(5);
        this.clientSecretTtl = clientSecretTtl != null ? clientSecretTtl : Duration.ofDays(90);
        this.secretBytes = secretBytes != null ? secretBytes : 32;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getOperatorClientIds() {
        return List.copyOf(operatorClientIds);
    }

    public List<String> getAllowedScopes() {
        return List.copyOf(allowedScopes);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getClientSecretTtl() {
        return clientSecretTtl;
    }

    public int getSecretBytes() {
        return secretBytes;
    }
}
