package dev.ainer.server.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出厂 {@code application.yaml} 的资源服务器信任锚配置契约。
 *
 * <p>{@code jwk-set-uri} 是「公钥从哪来」的唯一开关。如果 YAML 里的 key 名写错（例如写成
 * {@code jwks-uri}），Spring 不会报错，只是静默忽略——运维设置了 {@code AINER_SECURITY_JWK_SET_URI}
 * 却毫无效果，资源服务器继续按 discovery 取公钥。这条测试把 key 名与环境变量名一起钉住。
 */
class ResourceServerTrustAnchorConfigurationTest {

    @Test
    @DisplayName("application.yaml 暴露 jwk-set-uri / issuer-uri / 明文放行开关，且都指向文档化的环境变量")
    void shippedApplicationYamlExposesTrustAnchorKeys() throws IOException {
        PropertySource<?> source = loadApplicationYaml();

        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                .isEqualTo("${AINER_SECURITY_JWK_SET_URI:}");
        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .isEqualTo("${AINER_SECURITY_ISSUER_URI:}");
        assertThat(source.getProperty("spring.security.oauth2.resourceserver.jwt.audiences"))
                .isEqualTo("${AINER_SECURITY_AUDIENCES:ainer-api}");
        assertThat(source.getProperty("ainer.security.resource-server.allow-insecure-jwk-set-http"))
                .isEqualTo("${AINER_SECURITY_ALLOW_INSECURE_JWK_SET_HTTP:false}");
    }

    private static PropertySource<?> loadApplicationYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        assertThat(sources).as("shipped application.yaml").isNotEmpty();
        return sources.getFirst();
    }
}
