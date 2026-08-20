package dev.ainer.server;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 运行时文档 spike 验证（springdoc 3.1.0 × Boot 4.1）：ainer-server 全模块装配下
 * `/v3/api-docs` 生成有效 OpenAPI JSON 并覆盖各业务模块路径；默认安全语义 fail-closed——
 * 未认证 401，真 JWT 放行。Swagger UI 资源同样受保护。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "springdoc.api-docs.path=/v3/api-docs",
                "springdoc.swagger-ui.path=/swagger-ui.html",
                "ainer.server.test-openapi=true"
        })
@AutoConfigureTestRestTemplate
class AinerServerOpenApiTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_server_openapi_test")
                    .withUsername("ainer")
                    .withPassword("ainer");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @BeforeEach
    void authenticate() {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(JwtTestSupport.signUserJwt(
                        RSA_JWK, ISSUER, AUDIENCE, "account:docs", "platform.metrics.read"));
            }
            return execution.execute(request, body);
        });
    }

    @Test
    void apiDocsUnauthenticatedIsUnauthorizedThenRealJwtServesModulePaths() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> anonymous = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs", String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "account:docs", "platform.metrics.read"));
        ResponseEntity<String> docs = restTemplate.exchange(
                "http://localhost:" + port + "/v3/api-docs", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        String body = docs.getBody();
        assertThat(body).startsWith("{");
        // 全模块装配：各业务模块的代表性路径必须出现在生成的 OpenAPI 合同中
        assertThat(body).contains("/api/workspaces");
        assertThat(body).contains("/api/files");
        assertThat(body).contains("/api/organization");
        assertThat(body).contains("/api/knowledge");
        assertThat(body).contains("/api/authorization");
        assertThat(body).contains("/api/ai/agents");
    }

    @Test
    void swaggerUiResourcesAreProtectedByDefault() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> anonymous = restTemplate.getForEntity(
                "http://localhost:" + port + "/swagger-ui.html", String.class);
        // 未认证访问 UI 入口被安全链拦截（401 或重定向后的受保护响应，不允许匿名 200）
        assertThat(anonymous.getStatusCode().value()).isIn(401, 302, 403);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class OpenApiJwtFixture {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.server.test-openapi", havingValue = "true")
        @Bean
        @org.springframework.context.annotation.Primary
        JwtDecoder openApiTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }
}
