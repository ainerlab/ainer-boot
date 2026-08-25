package dev.ainer.observability;

import dev.ainer.observability.autoconfigure.AinerObservabilityAutoConfiguration;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AinerObservabilityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerObservabilityAutoConfiguration.class));

    @Test
    void observationAndCorrelationAssembleByDefaultWithoutOtlp() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context).hasSingleBean(RequestTraceCorrelationFilter.class);
            assertThat(context).doesNotHaveBean(AinerOtlpExportMarker.class);
        });
    }

    @Test
    void disabledSkipsObservabilityBeans() {
        runner.withPropertyValues("ainer.observability.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RequestTraceCorrelationFilter.class);
            assertThat(context).doesNotHaveBean(AinerOtlpExportMarker.class);
        });
    }

    @Test
    void otlpRemainsOffUnlessExplicitlyEnabled() {
        runner.withPropertyValues("ainer.observability.otlp.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context).doesNotHaveBean(AinerOtlpExportMarker.class);
        });
    }

    @Test
    void otlpMarkerAssemblesOnlyWhenEnabled() {
        runner.withPropertyValues("ainer.observability.otlp.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(AinerOtlpExportMarker.class);
            assertThat(context).hasSingleBean(ObservationRegistry.class);
        });
    }
}
