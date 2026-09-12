package dev.ainer.authorizationserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签名密钥配置的绑定契约。
 *
 * <p>两件事必须被钉住，否则会出现「运维改了配置、进程毫无反应」这种最难排查的形态：
 *
 * <ol>
 *   <li>配置键名与出厂 {@code application.yaml} 一致——YAML 里 key 名写错时 Spring 不会报错，
 *       只会静默忽略，于是环境变量永远不生效；</li>
 *   <li>缺省（什么都不配）时密钥环为空，装载必须失败关闭，而不是带着「没有签名 key」启动。</li>
 * </ol>
 */
class AinerAuthorizationServerSigningKeyBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Registrar.class);

    @Configuration
    @EnableConfigurationProperties(AinerAuthorizationServerProperties.class)
    static class Registrar {
    }

    @Test
    @DisplayName("signing-key-ring 的两个配置键能绑定到属性对象")
    void bindsKeyRingProperties() {
        runner.withPropertyValues(
                        "ainer.security.authorization-server.signing-key-ring.directory=file:/etc/ainer/keys",
                        "ainer.security.authorization-server.signing-key-ring.active-key-id=auth-2026q4")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(AinerAuthorizationServerProperties.class)
                        .satisfies(properties -> {
                            assertThat(properties.getSigningKeyRing().getDirectory())
                                    .isEqualTo("file:/etc/ainer/keys");
                            assertThat(properties.getSigningKeyRing().getActiveKeyId()).isEqualTo("auth-2026q4");
                        }));
    }

    @Test
    @DisplayName("缺省配置下密钥环为空，装载失败关闭")
    void defaultConfigurationFailsClosedOnLoad() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .getBean(AinerAuthorizationServerProperties.class)
                .satisfies(properties -> {
                    assertThat(properties.getSigningKeyRing().getDirectory()).isNull();
                    assertThat(properties.getSigningKey().getKeyId()).isNull();
                    assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> SigningKeyRing.load(
                                    properties.getSigningKey(), properties.getSigningKeyRing(), null)))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("requires a signing key");
                }));
    }

    @Test
    @DisplayName("出厂 application.yaml 暴露的配置键与环境变量名必须与代码一致")
    void shippedApplicationYamlExposesDocumentedKeys() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        assertThat(sources).isNotEmpty();
        PropertySource<?> source = sources.getFirst();

        assertThat(source.getProperty("ainer.security.authorization-server.signing-key-ring.directory"))
                .isEqualTo("${AINER_AUTHORIZATION_SIGNING_KEY_DIRECTORY:}");
        assertThat(source.getProperty("ainer.security.authorization-server.signing-key-ring.active-key-id"))
                .isEqualTo("${AINER_AUTHORIZATION_SIGNING_KEY_ACTIVE_ID:}");
        // 历史单文件形态仍在，轮换说明见 docs/security.md
        assertThat(source.getProperty("ainer.security.authorization-server.signing-key.key-id"))
                .isEqualTo("${AINER_AUTHORIZATION_SIGNING_KEY_ID:}");
    }
}
