package dev.ainer.authorizationserver.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("ainer.security.authorization-server.login-rate-limit")
public final class AinerLoginRateLimitProperties {

    private static final Set<String> DEFAULT_PATHS = Set.of(
            "/login", "/login/webauthn", "/webauthn/authenticate/options");

    private final boolean enabled;
    private final Duration window;
    private final int maxRequests;
    private final Set<String> paths;

    public AinerLoginRateLimitProperties(boolean enabled, Duration window, Integer maxRequests, Set<String> paths) {
        this.enabled = enabled;
        this.window = window != null ? window : Duration.ofMinutes(1);
        this.maxRequests = maxRequests != null ? maxRequests : 20;
        this.paths = (paths == null || paths.isEmpty())
                ? new LinkedHashSet<>(DEFAULT_PATHS)
                : new LinkedHashSet<>(paths);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public Set<String> getPaths() {
        return new LinkedHashSet<>(paths);
    }

    void validate() {
        if (paths == null
                || paths.isEmpty()
                || paths.stream().anyMatch(path -> path == null || path.isBlank() || !path.startsWith("/"))) {
            throw new IllegalStateException(
                    "Ainer login rate limit paths must be a non-empty set of absolute paths");
        }
    }
}
