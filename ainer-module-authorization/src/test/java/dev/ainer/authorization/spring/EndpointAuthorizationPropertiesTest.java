package dev.ainer.authorization.spring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ainer.security.endpoint-authorization.*} 的默认值契约：未声明端点的默认处置必须是
 * {@code FAIL_CLOSED}。默认值改回「宽容」等于把安全默认值交给宿主自觉，属于回归。
 */
class EndpointAuthorizationPropertiesTest {

    @Test
    void defaultsToFailClosed() {
        var properties = new EndpointAuthorizationProperties(null, null);

        assertThat(properties.getMode()).isEqualTo(EndpointAuthorizationMode.FAIL_CLOSED);
        assertThat(properties.getFrameworkHandlerPackages())
                .containsExactlyElementsOf(
                        EndpointAuthorizationProperties.DEFAULT_FRAMEWORK_HANDLER_PACKAGES)
                .contains("org.springframework.")
                .contains("org.springdoc.");
    }

    @Test
    void explicitConfigurationWins() {
        var properties = new EndpointAuthorizationProperties(
                EndpointAuthorizationMode.WARN, List.of("com.example.platform."));

        assertThat(properties.getMode()).isEqualTo(EndpointAuthorizationMode.WARN);
        assertThat(properties.getFrameworkHandlerPackages()).containsExactly("com.example.platform.");
    }
}
