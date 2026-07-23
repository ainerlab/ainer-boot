package dev.ainer.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.security.resource-server")
public class AinerResourceServerProperties {

    private boolean enabled;
    private String subjectClaim = "sub";
    private String tenantClaim = "tenant_id";
    private final OnlineValidation onlineValidation = new OnlineValidation();
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/platform/info",
            "/actuator/health/**",
            "/actuator/info"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSubjectClaim() {
        return subjectClaim;
    }

    public void setSubjectClaim(String subjectClaim) {
        this.subjectClaim = subjectClaim;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public OnlineValidation getOnlineValidation() {
        return onlineValidation;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = new ArrayList<>(publicPaths);
    }

    public static final class OnlineValidation {

        private boolean enabled;
        private String introspectionUri;
        private String clientId;
        private String clientSecret;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(2);
        private boolean allowInsecureHttp;
        private List<String> alwaysProtectedPaths = new ArrayList<>(List.of(
                "/internal/**",
                "/api/workspaces/*/authorization-audits"));
        private List<String> mutatingProtectedPaths = new ArrayList<>(List.of(
                "/api/workspaces/**",
                "/api/ai/**"));
        private List<HttpMethod> mutatingMethods = new ArrayList<>(List.of(
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIntrospectionUri() {
            return introspectionUri;
        }

        public void setIntrospectionUri(String introspectionUri) {
            this.introspectionUri = introspectionUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public void setAllowInsecureHttp(boolean allowInsecureHttp) {
            this.allowInsecureHttp = allowInsecureHttp;
        }

        public List<String> getAlwaysProtectedPaths() {
            return alwaysProtectedPaths;
        }

        public void setAlwaysProtectedPaths(List<String> alwaysProtectedPaths) {
            this.alwaysProtectedPaths = mutableCopy(alwaysProtectedPaths);
        }

        public List<String> getMutatingProtectedPaths() {
            return mutatingProtectedPaths;
        }

        public void setMutatingProtectedPaths(List<String> mutatingProtectedPaths) {
            this.mutatingProtectedPaths = mutableCopy(mutatingProtectedPaths);
        }

        public List<HttpMethod> getMutatingMethods() {
            return mutatingMethods;
        }

        public void setMutatingMethods(List<HttpMethod> mutatingMethods) {
            this.mutatingMethods = mutableCopy(mutatingMethods);
        }

        URI validateAndGetIntrospectionUri() {
            requireText(clientId, "client id");
            requireText(clientSecret, "client secret");
            requirePositive(connectTimeout, "connect timeout");
            requirePositive(readTimeout, "read timeout");
            validatePaths(alwaysProtectedPaths, "always-protected paths");
            validatePaths(mutatingProtectedPaths, "mutating-protected paths");
            if (alwaysProtectedPaths.isEmpty()
                    && (mutatingProtectedPaths.isEmpty() || mutatingMethods.isEmpty())) {
                throw new IllegalStateException("Ainer online token validation requires at least one protected rule");
            }

            URI uri;
            try {
                uri = URI.create(introspectionUri);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Ainer online token introspection URI is invalid", exception);
            }
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalStateException("Ainer online token introspection URI must be an absolute server URL");
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return uri;
            }
            if ("http".equalsIgnoreCase(uri.getScheme())
                    && allowInsecureHttp
                    && isLoopbackHost(uri.getHost())) {
                return uri;
            }
            throw new IllegalStateException(
                    "Ainer online token introspection URI must use HTTPS; HTTP is allowed only for loopback tests");
        }

        private static boolean isLoopbackHost(String host) {
            return "localhost".equalsIgnoreCase(host)
                    || "::1".equals(host)
                    || host.startsWith("127.");
        }

        private static void validatePaths(List<String> paths, String name) {
            if (paths == null || paths.stream().anyMatch(path -> path == null || !path.startsWith("/"))) {
                throw new IllegalStateException("Ainer online token validation " + name + " are invalid");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Ainer online token validation " + name + " is required");
            }
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalStateException("Ainer online token validation " + name + " must be positive");
            }
        }

        private static <T> List<T> mutableCopy(List<T> values) {
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        }
    }
}
