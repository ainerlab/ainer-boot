package dev.ainer.jwksprobe;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用的最小 Resource Server 发行物，用来在真实 HTTP 上证明「信任锚」这一跳。
 *
 * <p>与 {@code dev.ainer.epochprobe.ResourceServerProbeApplication} 的关键区别：**本发行物不声明
 * 任何 {@code JwtDecoder}**。验签公钥完全由生产装配决定——Spring Boot 的
 * {@code OAuth2ResourceServerAutoConfiguration} 依据
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}（取 JWKS）与
 * {@code ...issuer-uri}（校验 iss）、{@code ...audiences}（校验 aud）构造解码器，安全链来自框架的
 * {@code ainer.security.resource-server} 自动装配。也就是说这里跑的就是 ainer-server 上线时的那条
 * 装配路径，没有任何测试替身参与验签。
 *
 * <p>刻意放在 {@code dev.ainer.jwksprobe} 包而不是 {@code dev.ainer.authorizationserver} 下：
 * 后者是 Authorization Server 的组件扫描范围，静态嵌套的 {@code @SpringBootConfiguration} 会被扫描
 * 进被测应用，污染真实发行物的装配。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(JwksTrustResourceServerProbeApplication.ProbeController.class)
public class JwksTrustResourceServerProbeApplication {

    /** 唯一端点：能进来就说明 Token 已通过生产装配的全部校验（签名 / iss / aud / 期限）。 */
    @RestController
    @RequestMapping("/probe")
    public static class ProbeController {

        @GetMapping("/secure")
        public ResponseEntity<Void> secure() {
            return ResponseEntity.ok().build();
        }
    }
}
