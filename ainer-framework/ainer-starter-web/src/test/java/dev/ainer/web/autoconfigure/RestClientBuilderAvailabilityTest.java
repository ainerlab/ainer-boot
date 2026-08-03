package dev.ainer.web.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for P1-3: verifies that {@code RestClient.Builder} bean is auto-configured and available
 * for injection. Ainer modules ({@code ainer-starter-security}, relay/directory configs) inject
 * {@code RestClient.Builder}; without {@code spring-boot-starter-restclient} on the classpath, the bean is
 * absent and the app fails to start. This test uses {@code RestClientAutoConfiguration} directly (no Docker).
 */
class RestClientBuilderAvailabilityTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class));

    @Test
    void restClientBuilderBeanIsAvailable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.Builder.class);
        });
    }
}
