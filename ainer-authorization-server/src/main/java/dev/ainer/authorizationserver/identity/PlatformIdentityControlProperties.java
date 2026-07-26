package dev.ainer.authorizationserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.identity.platform-control")
public class PlatformIdentityControlProperties {

    private boolean enabled;
    private List<String> operatorClientIds = new ArrayList<>();
    private Duration requestTtl = Duration.ofDays(7);
    private Duration activationTtl = Duration.ofHours(24);
    private int activationMaxAttempts = 5;
    private String notificationProtectionActiveKeyVersion = "";
    private List<String> notificationProtectionKeys = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getOperatorClientIds() {
        return new ArrayList<>(operatorClientIds);
    }

    public void setOperatorClientIds(List<String> operatorClientIds) {
        this.operatorClientIds = operatorClientIds == null
                ? new ArrayList<>()
                : new ArrayList<>(operatorClientIds);
    }

    public Duration getRequestTtl() {
        return requestTtl;
    }

    public void setRequestTtl(Duration requestTtl) {
        this.requestTtl = requestTtl;
    }

    public Duration getActivationTtl() {
        return activationTtl;
    }

    public void setActivationTtl(Duration activationTtl) {
        this.activationTtl = activationTtl;
    }

    public int getActivationMaxAttempts() {
        return activationMaxAttempts;
    }

    public void setActivationMaxAttempts(int activationMaxAttempts) {
        this.activationMaxAttempts = activationMaxAttempts;
    }

    public String getNotificationProtectionActiveKeyVersion() {
        return notificationProtectionActiveKeyVersion;
    }

    public void setNotificationProtectionActiveKeyVersion(
            String notificationProtectionActiveKeyVersion) {
        this.notificationProtectionActiveKeyVersion =
                notificationProtectionActiveKeyVersion == null
                        ? ""
                        : notificationProtectionActiveKeyVersion;
    }

    public List<String> getNotificationProtectionKeys() {
        return new ArrayList<>(notificationProtectionKeys);
    }

    public void setNotificationProtectionKeys(List<String> notificationProtectionKeys) {
        this.notificationProtectionKeys = notificationProtectionKeys == null
                ? new ArrayList<>()
                : new ArrayList<>(notificationProtectionKeys);
    }
}
