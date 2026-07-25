package dev.ainer.authorizationserver.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("ainer.security.authorization-server.login-rate-limit")
public final class AinerLoginRateLimitProperties {

    private boolean enabled;
    private Duration window = Duration.ofMinutes(1);
    private int maxRequests = 20;
    private Set<String> paths = new LinkedHashSet<>(Set.of("/login", "/login/webauthn"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public Set<String> getPaths() {
        return new LinkedHashSet<>(paths);
    }

    public void setPaths(Set<String> paths) {
        this.paths = paths == null ? new LinkedHashSet<>() : new LinkedHashSet<>(paths);
    }
}
