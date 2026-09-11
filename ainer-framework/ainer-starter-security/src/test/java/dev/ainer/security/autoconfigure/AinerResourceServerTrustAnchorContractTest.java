package dev.ainer.security.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 Spring Boot 资源服务器解码器装配的既有事实——本框架的信任锚校验正是为它而存在。
 *
 * <p>这两条断言看起来像在测上游，但它们保证的是我们自己的判断依据：一旦 Boot 改变
 * 「jwk-set-uri 与 issuer-uri 同时存在时谁生效」或「空 issuer-uri 怎么绑定」的语义，
 * {@code AinerResourceServerProperties#resolveJwkSetTrustAnchor} 的失败关闭理由就要重新评估，
 * 而不是继续以旧假设静默运行。
 */
class AinerResourceServerTrustAnchorContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OAuth2ResourceServerAutoConfiguration.class));

    @Test
    @DisplayName("什么都不配时不构造解码器：资源服务器不可能带着没有信任锚的装配启动")
    void noTrustAnchorMeansNoDecoder() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
            OAuth2ResourceServerProperties properties = context.getBean(OAuth2ResourceServerProperties.class);
            assertThat(properties.getJwt().getIssuerUri()).isNull();
            assertThat(properties.getJwt().getJwkSetUri()).isNull();
        });
    }

    @Test
    @DisplayName("jwk-set-uri 生效：用 JWK 地址取公钥，空 issuer-uri 不会被当成「未配置」")
    void jwkSetUriWinsOverDiscoveryAndEmptyIssuerUriBindsAsEmptyString() {
        runner.withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.example.com/oauth2/jwks",
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
                        "spring.security.oauth2.resourceserver.jwt.audiences=ainer-api")
                .run(context -> {
                    assertThat(context).hasBean("jwtDecoderByJwkKeySetUri");
                    assertThat(context).doesNotHaveBean("jwtDecoderByIssuerUri");
                    // ""（而不是 null）才是必须被框架拦下的原因：解码器会拿空串校验 iss
                    assertThat(context.getBean(OAuth2ResourceServerProperties.class).getJwt().getIssuerUri())
                            .isEmpty();
                });
    }
}
