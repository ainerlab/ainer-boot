package dev.ainer.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-Docker binding test proving {@code @DurationMin} validates {@link java.time.Duration} fields without
 * the HV000030 failure that {@code @Positive} produced on Duration (ADR-0029 P0-3 review finding).
 */
class WorkspaceOwnerRecoveryPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Registrar.class);

    @Configuration
    @EnableConfigurationProperties(WorkspaceOwnerRecoveryProperties.class)
    static class Registrar {
    }

    @Test
    void bindsValidPositiveDurationWithoutValidationFailure() {
        runner.withPropertyValues(
                "ainer.workspace.owner-recovery.enabled=true",
                "ainer.workspace.owner-recovery.approval-ttl=15m")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(WorkspaceOwnerRecoveryProperties.class)
                        .satisfies(properties -> assertThat(properties.getApprovalTtl()).hasSeconds(900)));
    }

    @Test
    void rejectsNonPositiveDuration() {
        runner.withPropertyValues(
                "ainer.workspace.owner-recovery.enabled=true",
                "ainer.workspace.owner-recovery.approval-ttl=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
