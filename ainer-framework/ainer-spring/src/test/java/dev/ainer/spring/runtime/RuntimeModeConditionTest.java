package dev.ainer.spring.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeModeConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerRuntimeAutoConfiguration.class))
            .withUserConfiguration(AdapterConfiguration.class);

    @Test
    void defaultsToMonolithAdapters() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("localAdapter");
            assertThat(context).doesNotHaveBean("remoteAdapter");
            assertThat(context.getBean(AinerRuntimeProperties.class).getMode()).isEqualTo(RuntimeMode.MONOLITH);
        });
    }

    @Test
    void selectsServiceAdaptersExplicitly() {
        contextRunner.withPropertyValues("ainer.runtime.mode=service").run(context -> {
            assertThat(context).doesNotHaveBean("localAdapter");
            assertThat(context).hasBean("remoteAdapter");
            assertThat(context.getBean(AinerRuntimeProperties.class).getMode()).isEqualTo(RuntimeMode.SERVICE);
        });
    }

    @Test
    void rejectsUnsupportedRuntimeMode() {
        contextRunner.withPropertyValues("ainer.runtime.mode=cluster")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class AdapterConfiguration {

        @Bean
        @ConditionalOnRuntimeMode(RuntimeMode.MONOLITH)
        String localAdapter() {
            return "local";
        }

        @Bean
        @ConditionalOnRuntimeMode(RuntimeMode.SERVICE)
        String remoteAdapter() {
            return "remote";
        }
    }
}
