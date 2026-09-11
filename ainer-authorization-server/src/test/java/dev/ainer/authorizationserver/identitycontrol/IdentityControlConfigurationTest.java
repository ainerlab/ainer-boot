package dev.ainer.authorizationserver.identitycontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份生命周期控制面的配置失败关闭：开关打开却没有合法可信 SERVICE `sub` 时必须启动失败，
 * 不能"开着但白名单是空的"——那等于把高危写路径交给任何持 scope 的服务。
 */
class IdentityControlConfigurationTest {

    private static final String ENABLED =
            "ainer.security.authorization-server.identity-control.enabled=true";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(IdentityControlConfiguration.class);

    @Test
    void enabledWithoutTrustedServiceIdFailsClosed() {
        runner.withPropertyValues(ENABLED)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithBlankTrustedServiceIdFailsClosed() {
        runner.withPropertyValues(ENABLED,
                        "ainer.security.authorization-server.identity-control.trusted-service-id=  ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithInvalidTrustedServiceIdFailsClosed() {
        runner.withPropertyValues(ENABLED,
                        "ainer.security.authorization-server.identity-control.trusted-service-id=bad id!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledDoesNotRegisterSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdentityControlSettings.class);
        });
    }

    @Test
    void enabledWithValidTrustedServiceIdRegistersTrimmedSettings() {
        runner.withPropertyValues(ENABLED,
                        "ainer.security.authorization-server.identity-control.trusted-service-id="
                                + " 019c7100-0000-7000-8000-000000000001 ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IdentityControlSettings.class).trustedServiceId())
                            .isEqualTo("019c7100-0000-7000-8000-000000000001");
                });
    }
}
