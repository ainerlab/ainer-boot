package dev.ainer.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code ainer.security.resource-server.*} 配置属性。
 *
 * <p>{@code publicPaths} 定义免认证路径（默认仅平台信息与健康检查）；在线校验
 * （OnlineValidation）配置 introspection 端点与受保护路径规则；StepUp 配置近期强认证
 * 门禁。除显式列出的 publicPaths 外，其余路径一律要求认证。
 */
@ConfigurationProperties("ainer.security.resource-server")
public class AinerResourceServerProperties {

    private final boolean enabled;
    private final String subjectClaim;
    private final OnlineValidation onlineValidation;
    private final StepUp stepUp;
    private final List<String> publicPaths;

    public AinerResourceServerProperties(
            boolean enabled,
            String subjectClaim,
            OnlineValidation onlineValidation,
            StepUp stepUp,
            List<String> publicPaths) {
        this.enabled = enabled;
        this.subjectClaim = subjectClaim != null ? subjectClaim : "sub";
        this.onlineValidation = onlineValidation != null
                ? onlineValidation
                : new OnlineValidation(false, null, null, null, null, null, false, null, null, null);
        this.stepUp = stepUp != null ? stepUp : new StepUp(false, null, null, null, null, null, null);
        this.publicPaths = publicPaths != null
                ? new ArrayList<>(publicPaths)
                : new ArrayList<>(List.of("/api/platform/info", "/actuator/health/**", "/actuator/info"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSubjectClaim() {
        return subjectClaim;
    }

    public OnlineValidation getOnlineValidation() {
        return onlineValidation;
    }

    public StepUp getStepUp() {
        return stepUp;
    }

    public List<String> getPublicPaths() {
        return List.copyOf(publicPaths);
    }

    public static final class OnlineValidation {

        private final boolean enabled;
        private final String introspectionUri;
        private final String clientId;
        private final String clientSecret;
        private final Duration connectTimeout;
        private final Duration readTimeout;
        private final boolean allowInsecureHttp;
        private final List<String> alwaysProtectedPaths;
        private final List<String> mutatingProtectedPaths;
        private final List<HttpMethod> mutatingMethods;

        public OnlineValidation(
                boolean enabled,
                String introspectionUri,
                String clientId,
                String clientSecret,
                Duration connectTimeout,
                Duration readTimeout,
                boolean allowInsecureHttp,
                List<String> alwaysProtectedPaths,
                List<String> mutatingProtectedPaths,
                List<HttpMethod> mutatingMethods) {
            this.enabled = enabled;
            this.introspectionUri = introspectionUri;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
            this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(2);
            this.allowInsecureHttp = allowInsecureHttp;
            this.alwaysProtectedPaths = alwaysProtectedPaths != null
                    ? new ArrayList<>(alwaysProtectedPaths)
                    : new ArrayList<>(List.of("/internal/**", "/api/workspaces/*/authorization-audits"));
            this.mutatingProtectedPaths = mutatingProtectedPaths != null
                    ? new ArrayList<>(mutatingProtectedPaths)
                    : new ArrayList<>(List.of("/api/workspaces/**", "/api/ai/**"));
            this.mutatingMethods = mutatingMethods != null
                    ? new ArrayList<>(mutatingMethods)
                    : new ArrayList<>(List.of(
                            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getIntrospectionUri() {
            return introspectionUri;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public List<String> getAlwaysProtectedPaths() {
            return List.copyOf(alwaysProtectedPaths);
        }

        public List<String> getMutatingProtectedPaths() {
            return List.copyOf(mutatingProtectedPaths);
        }

        public List<HttpMethod> getMutatingMethods() {
            return List.copyOf(mutatingMethods);
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
    }

    public static final class StepUp {

        private final boolean enabled;
        private final Duration maxAuthAge;
        private final Duration clockSkew;
        private final List<String> requiredAmr;
        private final List<String> alwaysProtectedPaths;
        private final List<String> mutatingProtectedPaths;
        private final List<HttpMethod> mutatingMethods;

        public StepUp(
                boolean enabled,
                Duration maxAuthAge,
                Duration clockSkew,
                List<String> requiredAmr,
                List<String> alwaysProtectedPaths,
                List<String> mutatingProtectedPaths,
                List<HttpMethod> mutatingMethods) {
            this.enabled = enabled;
            this.maxAuthAge = maxAuthAge != null ? maxAuthAge : Duration.ofMinutes(15);
            this.clockSkew = clockSkew != null ? clockSkew : Duration.ofSeconds(60);
            this.requiredAmr = requiredAmr != null ? new ArrayList<>(requiredAmr)
                    : new ArrayList<>(List.of("mfa"));
            this.alwaysProtectedPaths = alwaysProtectedPaths != null
                    ? new ArrayList<>(alwaysProtectedPaths)
                    : new ArrayList<>();
            this.mutatingProtectedPaths = mutatingProtectedPaths != null
                    ? new ArrayList<>(mutatingProtectedPaths)
                    : new ArrayList<>();
            this.mutatingMethods = mutatingMethods != null
                    ? new ArrayList<>(mutatingMethods)
                    : new ArrayList<>(List.of(
                            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Duration getMaxAuthAge() {
            return maxAuthAge;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public List<String> getRequiredAmr() {
            return List.copyOf(requiredAmr);
        }

        public List<String> getAlwaysProtectedPaths() {
            return List.copyOf(alwaysProtectedPaths);
        }

        public List<String> getMutatingProtectedPaths() {
            return List.copyOf(mutatingProtectedPaths);
        }

        public List<HttpMethod> getMutatingMethods() {
            return List.copyOf(mutatingMethods);
        }

        void validate() {
            if (!enabled) {
                return;
            }
            requirePositive(maxAuthAge, "max-auth-age");
            if (maxAuthAge.toHours() > 24) {
                throw new IllegalStateException("Ainer step-up max-auth-age must be at most 24 hours");
            }
            if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalStateException(
                        "Ainer step-up clock-skew must be between zero and 5 minutes");
            }
            if (requiredAmr == null || requiredAmr.isEmpty() || requiredAmr.stream().anyMatch(String::isBlank)) {
                throw new IllegalStateException("Ainer step-up required-amr must be a non-empty list");
            }
            validatePaths(alwaysProtectedPaths, "always-protected paths");
            validatePaths(mutatingProtectedPaths, "mutating-protected paths");
            if (alwaysProtectedPaths.isEmpty()
                    && (mutatingProtectedPaths.isEmpty() || mutatingMethods.isEmpty())) {
                throw new IllegalStateException("Ainer step-up requires at least one protected rule");
            }
        }

        private static void validatePaths(List<String> paths, String name) {
            if (paths != null && paths.stream().anyMatch(path -> path == null || !path.startsWith("/"))) {
                throw new IllegalStateException("Ainer step-up " + name + " are invalid");
            }
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalStateException("Ainer step-up " + name + " must be positive");
            }
        }
    }
}
