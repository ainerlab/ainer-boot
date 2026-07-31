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
