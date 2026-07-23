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
                new AinerResourceServerProperties().getOnlineValidation();

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
        AinerResourceServerProperties.OnlineValidation properties = validProperties();
        properties.setIntrospectionUri("http://127.0.0.1:9000/oauth2/introspect");

        assertThatThrownBy(properties::validateAndGetIntrospectionUri)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");

        properties.setAllowInsecureHttp(true);
        assertThat(properties.validateAndGetIntrospectionUri()).hasScheme("http");
    }

    @Test
    void nonLoopbackHttpIsRejectedEvenWithInsecureOptIn() {
        AinerResourceServerProperties.OnlineValidation properties = validProperties();
        properties.setIntrospectionUri("http://auth.example.com/oauth2/introspect");
        properties.setAllowInsecureHttp(true);

        assertThatThrownBy(properties::validateAndGetIntrospectionUri)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void missingSecretNonPositiveTimeoutAndEmptyRulesFailClosed() {
        AinerResourceServerProperties.OnlineValidation missingSecret = validProperties();
        missingSecret.setClientSecret(" ");
        assertThatThrownBy(missingSecret::validateAndGetIntrospectionUri)
                .hasMessageContaining("client secret");

        AinerResourceServerProperties.OnlineValidation invalidTimeout = validProperties();
        invalidTimeout.setReadTimeout(Duration.ZERO);
        assertThatThrownBy(invalidTimeout::validateAndGetIntrospectionUri)
                .hasMessageContaining("read timeout");

        AinerResourceServerProperties.OnlineValidation emptyRules = validProperties();
        emptyRules.setAlwaysProtectedPaths(List.of());
        emptyRules.setMutatingProtectedPaths(List.of());
        assertThatThrownBy(emptyRules::validateAndGetIntrospectionUri)
                .hasMessageContaining("at least one protected rule");
    }

    private AinerResourceServerProperties.OnlineValidation validProperties() {
        AinerResourceServerProperties.OnlineValidation properties =
                new AinerResourceServerProperties().getOnlineValidation();
        properties.setEnabled(true);
        properties.setIntrospectionUri("https://auth.example.com/oauth2/introspect");
        properties.setClientId("ainer-resource-server");
        properties.setClientSecret("test-only-introspection-secret");
        return properties;
    }
}
