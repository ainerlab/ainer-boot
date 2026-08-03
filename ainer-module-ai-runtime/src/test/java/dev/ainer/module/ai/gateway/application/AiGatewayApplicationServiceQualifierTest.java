package dev.ainer.module.ai.gateway.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for P1-2: verifies that {@link AiGatewayApplicationService} explicitly qualifies the
 * {@code ExecutorService} injection point with {@code @Qualifier("aiStreamExecutor")}, so that the bean
 * marked {@code @Bean(defaultCandidate = false)} is resolvable. Without the qualifier, Spring 7 cannot
 * inject a non-default-candidate bean by type alone, and the AI module fails to start when enabled.
 */
class AiGatewayApplicationServiceQualifierTest {

    @Test
    void streamExecutorParameterIsQualifiedWithAiStreamExecutor() {
        var constructor = Arrays.stream(AiGatewayApplicationService.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() > 0)
                .findFirst()
                .orElseThrow();

        Parameter streamExecutorParam = Arrays.stream(constructor.getParameters())
                .filter(p -> p.getType() == ExecutorService.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ExecutorService parameter found"));

        Qualifier qualifier = streamExecutorParam.getAnnotation(Qualifier.class);
        assertThat(qualifier)
                .as("@Qualifier must be present on ExecutorService injection point")
                .isNotNull();
        assertThat(qualifier.value()).isEqualTo("aiStreamExecutor");
    }
}
