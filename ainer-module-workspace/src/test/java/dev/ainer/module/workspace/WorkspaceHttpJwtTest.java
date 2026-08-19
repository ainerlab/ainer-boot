package dev.ainer.module.workspace;

import com.jayway.jsonpath.JsonPath;
import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workspace `/api/workspaces` 的真实 JWT HTTP 门禁测试：真 RSA 签名 USER_NEUTRAL_V1 +
 * NimbusJwtDecoder 验签完整安全链（补齐此前仅有服务层集成测试的缺口）。覆盖 401/403/
 * 201/本人读取/非成员 404 不泄露存在性。
 */
@Testcontainers
@SpringBootTest(
        classes = WorkspaceHttpJwtTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.workspace.enabled=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.workspace.test-http-jwt=true"
        })
@AutoConfigureTestRestTemplate
class WorkspaceHttpJwtTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final String OWNER = "workspace-owner-1";
    private static final String OUTSIDER = "workspace-outsider-1";
    /** requireScope 会剥掉常量的 SCOPE_ 前缀后比较，因此 JWT scope claim 使用无前缀值。 */
    private static final String SCOPES = "workspace.read workspace.write";
    private static final com.nimbusds.jose.jwk.RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.3-alpine");

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
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanAndAuthenticateAsOwner() {
        jdbcTemplate.execute("DELETE FROM ainer_workspace_authorization_audit");
        jdbcTemplate.execute("DELETE FROM ainer_workspace_member");
        jdbcTemplate.execute("DELETE FROM ainer_workspace");
        authenticateAs(JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, OWNER, SCOPES));
    }

    private void authenticateAs(String jwt) {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void missingTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> response =
                restTemplate.getForEntity(url("/api/workspaces"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("AINER.COMMON.UNAUTHENTICATED");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void tokenWithoutScopeIsForbidden() {
        authenticateAs(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, OWNER, "unrelated.read"));
        ResponseEntity<String> response =
                restTemplate.postForEntity(url("/api/workspaces"), json("{\"name\": \"无权空间\"}"),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void ownerCreatesReadsOwnWorkspaceAndOutsiderGetsNotFound() {
        ResponseEntity<String> created = restTemplate.postForEntity(
                url("/api/workspaces"), json("{\"name\": \"门禁验证空间\"}"), String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        String id = JsonPath.parse(created.getBody()).read("$.data.id", String.class);
        assertThat(id).isNotBlank();

        ResponseEntity<String> fetched = restTemplate.exchange(
                url("/api/workspaces/" + id), HttpMethod.GET, null, String.class);
        assertThat(fetched.getStatusCode().value()).isEqualTo(200);
        assertThat(fetched.getBody()).contains("门禁验证空间");

        // 非成员 outsider 读取他人 Workspace：404 不泄露存在性
        authenticateAs(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, OUTSIDER, SCOPES));
        ResponseEntity<String> cross = restTemplate.exchange(
                url("/api/workspaces/" + id), HttpMethod.GET, null, String.class);
        assertThat(cross.getStatusCode().value()).isEqualTo(404);
        assertThat(cross.getBody()).contains("AINER.WORKSPACE.NOT_FOUND");

        // 管理员等真实表由服务层集成测试覆盖；此处补记授权审计行存在
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_workspace_authorization_audit WHERE workspace_id = ?",
                Integer.class, UUID.fromString(id));
        assertThat(audits).isGreaterThanOrEqualTo(1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WorkspaceModuleConfiguration.class)
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.workspace.test-http-jwt", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder workspaceTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }

    }
}
