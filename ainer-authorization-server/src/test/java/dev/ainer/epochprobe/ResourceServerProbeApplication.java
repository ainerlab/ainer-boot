package dev.ainer.epochprobe;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用的最小 Resource Server 发行物，用来在真实 HTTP 上验证
 * {@code OnlineAccessTokenValidationFilter}（RFC 7662 在线校验）路径。
 *
 * <p>刻意放在 {@code dev.ainer.epochprobe} 包而不是 {@code dev.ainer.authorizationserver} 下：
 * 后者是 Authorization Server 的组件扫描范围，静态嵌套的 {@code @SpringBootConfiguration}
 * 会被扫描进被测应用，污染真实发行物的装配。
 *
 * <p>只暴露探针端点 {@code GET /probe/secure}，保护完全来自框架的
 * {@code ainer.security.resource-server} 自动装配：关闭在线校验时只校验 JWT
 * （签名/issuer/audience/期限），开启后追加 introspection，inactive 返回 401。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ResourceServerProbeApplication.ProbeController.class)
public class ResourceServerProbeApplication {

    /**
     * 真实 JWKS 解码：从被测 Authorization Server 拉取 JWK Set，签名与 issuer/audience
     * 都是真实校验，不是测试替身。
     */
    @Bean
    JwtDecoder probeJwtDecoder(
            @Value("${probe.jwk-set-uri}") String jwkSetUri,
            @Value("${probe.issuer}") String issuer,
            @Value("${probe.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator, audienceValidator));
        return decoder;
    }

    @RestController
    @RequestMapping("/probe")
    public static class ProbeController {

        @GetMapping("/secure")
        public ResponseEntity<Void> secure() {
            return ResponseEntity.ok().build();
        }
    }
}
