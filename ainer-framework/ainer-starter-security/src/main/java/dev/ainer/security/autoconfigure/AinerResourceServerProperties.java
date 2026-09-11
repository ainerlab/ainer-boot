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
 *
 * <p>{@code allowInsecureJwkSetHttp} 只作用于 Spring Boot 的 JWKS 信任锚
 * （{@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}）：该 URI 决定资源服务器
 * 从哪里取验签公钥，明文 HTTP 且非环回时等价于把签名验证交给网络中间人，因此默认失败关闭。
 */
@ConfigurationProperties("ainer.security.resource-server")
public class AinerResourceServerProperties {

    private final boolean enabled;
    private final String subjectClaim;
    private final OnlineValidation onlineValidation;
    private final StepUp stepUp;
    private final List<String> publicPaths;
    private final boolean allowInsecureJwkSetHttp;

    public AinerResourceServerProperties(
            boolean enabled,
            String subjectClaim,
            OnlineValidation onlineValidation,
            StepUp stepUp,
            List<String> publicPaths,
            boolean allowInsecureJwkSetHttp) {
        this.enabled = enabled;
        this.subjectClaim = subjectClaim != null ? subjectClaim : "sub";
        this.onlineValidation = onlineValidation != null
                ? onlineValidation
                : new OnlineValidation(false, null, null, null, null, null, false, null, null, null);
        this.stepUp = stepUp != null ? stepUp : new StepUp(false, null, null, null, null, null, null);
        this.publicPaths = publicPaths != null
                ? new ArrayList<>(publicPaths)
                : new ArrayList<>(List.of("/api/platform/info", "/actuator/health/**", "/actuator/info"));
        this.allowInsecureJwkSetHttp = allowInsecureJwkSetHttp;
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

    public boolean isAllowInsecureJwkSetHttp() {
        return allowInsecureJwkSetHttp;
    }

    /**
     * 校验 JWKS 信任锚 URI（来源是 Spring Boot 的
     * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}；未配置时返回 {@code null}）。
     *
     * <p>Spring Security 的 {@code withJwkSetUri} 不做任何 scheme 检查，明文地址会被照单全收；
     * 而 JWKS 是整条验签链的信任锚，从明文地址取公钥意味着网络中间人可以替换公钥并伪造 Token。
     * 因此这里在启动期失败关闭：只接受 HTTPS，明文 HTTP 仅允许环回地址且必须显式放行。
     */
    URI validateAndGetJwkSetUri(String jwkSetUri) {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(jwkSetUri.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Ainer resource server JWK Set URI is invalid", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Ainer resource server JWK Set URI must be an absolute server URL");
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return uri;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())
                && allowInsecureJwkSetHttp
                && isLoopbackHost(uri.getHost())) {
            return uri;
        }
        throw new IllegalStateException("Ainer resource server JWK Set URI must use HTTPS; HTTP is allowed only "
                + "for loopback tests (ainer.security.resource-server.allow-insecure-jwk-set-http=true)");
    }

    /**
     * 解析资源服务器的 JWKS 信任锚，并在启动期拦下一个静默故障：显式配置了 {@code jwk-set-uri}
     * 却把 {@code issuer-uri} 留空。
     *
     * <p>Spring Boot 把空字符串绑定成 {@code ""} 而不是 {@code null}，于是在这种组合下会构造出
     * 「取 JWKS 走 jwk-set-uri、校验 iss 却拿空字符串比」的解码器：进程正常启动，但**所有** Token
     * 都验不过（`iss` 永远不等于空串），表现为「配置生效了，但谁都进不来」——没有任何启动期报错。
     * 授权服务器 issuer 与 JWKS 是同一个信任锚的两半，缺一不可，因此这里失败关闭。
     */
    AinerJwkSetTrustAnchor resolveJwkSetTrustAnchor(String jwkSetUri, String issuerUri) {
        URI uri = validateAndGetJwkSetUri(jwkSetUri);
        if (uri != null && (issuerUri == null || issuerUri.isBlank())) {
            throw new IllegalStateException("Ainer resource server requires an issuer URI together with the JWK Set "
                    + "URI: Spring Boot would validate the iss claim against an empty issuer and reject every "
                    + "token (configure spring.security.oauth2.resourceserver.jwt.issuer-uri)");
        }
        return new AinerJwkSetTrustAnchor(uri, issuerUri == null || issuerUri.isBlank() ? null : issuerUri);
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || host.startsWith("127.");
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
            return AinerResourceServerProperties.isLoopbackHost(host);
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
