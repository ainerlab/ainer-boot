package dev.ainer.security.autoconfigure;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerResourceServerPropertiesTest {

    @Test
    void onlineValidationIsDisabledByDefaultWithExplicitProtectedRules() {
        AinerResourceServerProperties.OnlineValidation properties =
                new AinerResourceServerProperties.OnlineValidation(
                        false, null, null, null, null, null, false, null, null, null);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getAlwaysProtectedPaths()).contains("/internal/**");
        assertThat(properties.getMutatingProtectedPaths()).contains("/api/workspaces/**", "/api/ai/**");
    }

    @Test
    void validHttpsConfigurationIsAccepted() {
        AinerResourceServerProperties.OnlineValidation properties = validProperties();

        assertThat(properties.validateAndGetIntrospectionUri())
                .hasScheme("https")
                .hasHost("auth.example.com");
    }

    @Test
    void loopbackHttpRequiresExplicitTestOptIn() {
        assertThatThrownBy(() -> onlineValidation(
                "http://127.0.0.1:9000/oauth2/introspect", false, "test-only-introspection-secret", null, null, null)
                .validateAndGetIntrospectionUri())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        assertThat(onlineValidation(
                "http://127.0.0.1:9000/oauth2/introspect", true, "test-only-introspection-secret", null, null, null)
                .validateAndGetIntrospectionUri()).hasScheme("http");
    }

    @Test
    void nonLoopbackHttpIsRejectedEvenWithInsecureOptIn() {
        assertThatThrownBy(() -> onlineValidation(
                "http://auth.example.com/oauth2/introspect", true, "test-only-introspection-secret", null, null, null)
                .validateAndGetIntrospectionUri())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void missingSecretNonPositiveTimeoutAndEmptyRulesFailClosed() {
        assertThatThrownBy(() -> onlineValidation(
                "https://auth.example.com/oauth2/introspect", false, " ", null, null, null)
                .validateAndGetIntrospectionUri())
                .hasMessageContaining("client secret");

        assertThatThrownBy(() -> onlineValidation(
                "https://auth.example.com/oauth2/introspect", false, "test-only-introspection-secret",
                Duration.ZERO, null, null)
                .validateAndGetIntrospectionUri())
                .hasMessageContaining("read timeout");

        assertThatThrownBy(() -> onlineValidation(
                "https://auth.example.com/oauth2/introspect", false, "test-only-introspection-secret",
                null, List.of(), List.of())
                .validateAndGetIntrospectionUri())
                .hasMessageContaining("at least one protected rule");
    }

    @Test
    void jwkSetUriIsUnsetByDefaultSoDiscoveryViaIssuerUriIsUnchanged() {
        AinerResourceServerProperties properties = resourceServer(false);

        assertThat(properties.validateAndGetJwkSetUri(null)).isNull();
        assertThat(properties.validateAndGetJwkSetUri("  ")).isNull();
        assertThat(properties.isAllowInsecureJwkSetHttp()).isFalse();
    }

    @Test
    void httpsJwkSetUriIsAccepted() {
        AinerResourceServerProperties properties = resourceServer(false);

        assertThat(properties.validateAndGetJwkSetUri("https://auth.example.com/oauth2/jwks"))
                .hasScheme("https")
                .hasHost("auth.example.com")
                .hasPath("/oauth2/jwks");
    }

    @Test
    void insecureJwkSetHttpFailsClosedInsteadOfTrustingTheNetwork() {
        // Spring Security 的 withJwkSetUri 不做 scheme 检查：不在启动期拦下，明文信任锚会被照单全收
        assertThatThrownBy(() -> resourceServer(false)
                .validateAndGetJwkSetUri("http://auth.example.com/oauth2/jwks"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        // 即使是环回地址，也必须显式放行；放行后仅环回可用
        assertThatThrownBy(() -> resourceServer(false)
                .validateAndGetJwkSetUri("http://127.0.0.1:9000/oauth2/jwks"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        assertThat(resourceServer(true).validateAndGetJwkSetUri("http://127.0.0.1:9000/oauth2/jwks"))
                .hasScheme("http");

        assertThatThrownBy(() -> resourceServer(true)
                .validateAndGetJwkSetUri("http://auth.example.com/oauth2/jwks"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void relativeOrUserInfoBearingJwkSetUriFailsClosed() {
        assertThatThrownBy(() -> resourceServer(false).validateAndGetJwkSetUri("/oauth2/jwks"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute");

        assertThatThrownBy(() -> resourceServer(false)
                .validateAndGetJwkSetUri("https://user:secret@auth.example.com/oauth2/jwks"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void jwkSetUriWithoutIssuerUriFailsClosed() {
        // Boot 会把空 issuer-uri 绑成 ""（不是 null），于是解码器校验 iss 时拿空串比较：
        // 进程能起来，但所有 Token 都 401，且没有任何启动期报错。
        assertThatThrownBy(() -> resourceServer(false)
                .resolveJwkSetTrustAnchor("https://auth.example.com/oauth2/jwks", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer URI");

        assertThatThrownBy(() -> resourceServer(false)
                .resolveJwkSetTrustAnchor("https://auth.example.com/oauth2/jwks", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer URI");
    }

    @Test
    void trustAnchorKeepsBothHalvesOfTheAnchor() {
        AinerResourceServerProperties properties = resourceServer(false);

        AinerJwkSetTrustAnchor explicit = properties.resolveJwkSetTrustAnchor(
                "https://auth.example.com/oauth2/jwks", "https://auth.example.com");
        assertThat(explicit.jwkSetUri()).hasToString("https://auth.example.com/oauth2/jwks");
        assertThat(explicit.issuerUri()).isEqualTo("https://auth.example.com");

        // 未配置 jwk-set-uri：信任锚仍然成立，公钥来源是 issuer-uri 的 discovery
        AinerJwkSetTrustAnchor discovery = properties.resolveJwkSetTrustAnchor("", "https://auth.example.com");
        assertThat(discovery.jwkSetUri()).isNull();
        assertThat(discovery.issuerUri()).isEqualTo("https://auth.example.com");
    }

    private static AinerResourceServerProperties resourceServer(boolean allowInsecureJwkSetHttp) {
        return new AinerResourceServerProperties(
                true, null, null, null, null, allowInsecureJwkSetHttp);
    }

    @Test
    void allowInsecureJwkSetHttpDefaultsToFalseAndBindsFromProperty() {
        bindingRunner.run(context -> assertThat(context).hasNotFailed()
                .getBean(AinerResourceServerProperties.class)
                .satisfies(properties -> assertThat(properties.isAllowInsecureJwkSetHttp()).isFalse()));

        bindingRunner.withPropertyValues("ainer.security.resource-server.allow-insecure-jwk-set-http=true")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(AinerResourceServerProperties.class)
                        .satisfies(properties -> assertThat(properties.isAllowInsecureJwkSetHttp()).isTrue()));
    }

    private final org.springframework.boot.test.context.runner.ApplicationContextRunner bindingRunner =
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                    .withUserConfiguration(Registrar.class);

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(AinerResourceServerProperties.class)
    static class Registrar {
    }

    private AinerResourceServerProperties.OnlineValidation validProperties() {
        return onlineValidation(
                "https://auth.example.com/oauth2/introspect", false, "test-only-introspection-secret",
                null, null, null);
    }

    private static AinerResourceServerProperties.OnlineValidation onlineValidation(
            String introspectionUri,
            boolean allowInsecureHttp,
            String clientSecret,
            Duration readTimeout,
            List<String> alwaysProtectedPaths,
            List<String> mutatingProtectedPaths) {
        return new AinerResourceServerProperties.OnlineValidation(
                true,
                introspectionUri,
                "ainer-resource-server",
                clientSecret,
                null,
                readTimeout,
                allowInsecureHttp,
                alwaysProtectedPaths,
                mutatingProtectedPaths,
                null);
    }
}
