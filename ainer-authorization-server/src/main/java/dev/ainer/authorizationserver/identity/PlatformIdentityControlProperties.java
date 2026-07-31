package dev.ainer.authorizationserver.identity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties("ainer.identity.platform-control")
public class PlatformIdentityControlProperties {

    private final boolean enabled;
    private final List<String> operatorClientIds;
    @Positive
    private final Duration requestTtl;
    @Positive
    private final Duration activationTtl;
    @Min(1)
    private final int activationMaxAttempts;
    private final String notificationProtectionActiveKeyVersion;
    private final List<String> notificationProtectionKeys;

    public PlatformIdentityControlProperties(
            boolean enabled,
            List<String> operatorClientIds,
            Duration requestTtl,
            Duration activationTtl,
            Integer activationMaxAttempts,
            String notificationProtectionActiveKeyVersion,
            List<String> notificationProtectionKeys) {
        this.enabled = enabled;
        this.operatorClientIds = operatorClientIds != null
                ? new ArrayList<>(operatorClientIds)
                : new ArrayList<>();
        this.requestTtl = requestTtl != null ? requestTtl : Duration.ofDays(7);
        this.activationTtl = activationTtl != null ? activationTtl : Duration.ofHours(24);
        this.activationMaxAttempts = activationMaxAttempts != null ? activationMaxAttempts : 5;
        this.notificationProtectionActiveKeyVersion =
                notificationProtectionActiveKeyVersion != null ? notificationProtectionActiveKeyVersion : "";
        this.notificationProtectionKeys = notificationProtectionKeys != null
                ? new ArrayList<>(notificationProtectionKeys)
                : new ArrayList<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getOperatorClientIds() {
        return new ArrayList<>(operatorClientIds);
    }

    public Duration getRequestTtl() {
        return requestTtl;
    }

    public Duration getActivationTtl() {
        return activationTtl;
    }

    public int getActivationMaxAttempts() {
        return activationMaxAttempts;
    }

    public String getNotificationProtectionActiveKeyVersion() {
        return notificationProtectionActiveKeyVersion;
    }

    public List<String> getNotificationProtectionKeys() {
        return new ArrayList<>(notificationProtectionKeys);
    }
}
