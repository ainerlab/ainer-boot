package dev.ainer.server.authorization;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端点授权默认拒绝的运行期证明（{@code ainer.security.endpoint-authorization.mode=fail-closed}）。
 *
 * <p>真 HTTP + 真签名 JWT + PostgreSQL Testcontainers（不使用 Mockito / H2）：
 * <ul>
 *   <li>未声明端点：已认证主体 403（改动前是静默放行）、匿名 401，并在日志留下 ERROR 行；</li>
 *   <li>{@code @AinerAuthorize} 端点：决策引擎行为不变（无 Binding 403，建 Binding 后 200）；</li>
 *   <li>{@code @EndpointAccess(PUBLIC)} + public-paths：匿名 200；</li>
 *   <li>{@code @EndpointAccess(AUTHENTICATED)} / 类级 {@code DELEGATED}：匿名 401、已认证 200；</li>
 *   <li>public-paths 里既有的真实端点 {@code /api/platform/info} 不受影响；</li>
 *   <li>第三方 handler（Actuator 健康检查、springdoc 文档、Spring Boot 错误分发）不被误拒。</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        classes = EndpointAuthorizationFailClosedTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.authorization.trusted-managers=https://auth.ainer.test|platform-ops",
                "ainer.security.resource-server.enabled=true",
                "ainer.security.endpoint-authorization.mode=fail-closed",
                "ainer.security.resource-server.public-paths[0]=/api/platform/info",
                "ainer.security.resource-server.public-paths[1]=/actuator/health",
                "ainer.security.resource-server.public-paths[2]=/actuator/health/**",
                "ainer.security.resource-server.public-paths[3]=/api/authz-public-probe",
                "springdoc.api-docs.path=/v3/api-docs",
                "ainer.server.test-endpoint-authorization=true"
        })
@AutoConfigureTestRestTemplate
class EndpointAuthorizationFailClosedTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c7000-0000-7000-8000-000000000010");
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_endpoint_authz_test")
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
    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestTestClient client;
    private String baseUrl;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("DELETE FROM ainer_authorization_decision_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_change_audit");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_set_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_acting_grant");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_subject_binding");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role_permission");
        jdbcTemplate.execute("DELETE FROM ainer_authorization_role");
        client = RestTestClient.forLocalServer(restTemplate, port);
        baseUrl = "http://localhost:" + port;
        restTemplate.getRestTemplate().setInterceptors(List.of());
    }

    private void authenticate(String jwt) {
        restTemplate.getRestTemplate().setInterceptors(List.of());
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private String userJwt(String subjectId, String scopes) {
        return JwtTestSupport.signUserJwt(RSA_JWK, ISSUER, AUDIENCE, subjectId, scopes);
    }

    private String opsJwt() {
        return JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "platform-ops", "authorization.manage");
    }

    private ResponseEntity<String> anonymousGet(String path) {
        restTemplate.getRestTemplate().setInterceptors(List.of());
        return restTemplate.getForEntity(baseUrl + path, String.class);
    }

    @Test
    void undeclaredEndpointIsDeniedForAuthenticatedSubject(CapturedOutput output) {
        authenticate(userJwt("endpoint-authz-user", "workspace.read"));

        RestResponse response = client.get("/api/authz-undeclared-probe");

        assertThat(response.status().value()).isEqualTo(403);
        assertThat(response.jsonPath("$.code")).isEqualTo("AINER.COMMON.FORBIDDEN");
        // 默认拒绝不能是静默的：日志必须留下可告警的 ERROR 行与端点身份
        assertThat(output.getOut())
                .contains("端点未声明授权口径，FAIL_CLOSED 拒绝")
                .contains("/api/authz-undeclared-probe")
                .contains("EndpointAuthorizationProbes$UndeclaredProbeController#peek");
    }

    @Test
    void undeclaredEndpointStillRequiresAuthentication() {
        ResponseEntity<String> response = anonymousGet("/api/authz-undeclared-probe");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void annotatedEndpointKeepsDecisionEngineBehaviour() {
        authenticate(userJwt("endpoint-authz-user", "workspace.read"));
        assertThat(client.get("/api/authz-annotated-probe").status().value()).isEqualTo(403);

        authenticate(opsJwt());
        RestResponse role = client.postJson("/api/authorization/roles", """
                {"code": "endpoint-authz-reader", "name": "Endpoint Authz Reader",
                 "permissions": ["workspace.read"]}
                """);
        assertThat(role.status().value()).isEqualTo(201);
        String roleId = (String) role.jsonPath("$.data.id");
        RestResponse binding = client.postJson("/api/authorization/bindings", """
                {"issuer": "%s", "subjectType": "USER", "subjectId": "endpoint-authz-user",
                 "roleId": "%s", "scopeKind": "WORKSPACE", "workspaceId": "%s"}
                """.formatted(ISSUER, roleId, WORKSPACE_ID));
        assertThat(binding.status().value()).isEqualTo(201);

        authenticate(userJwt("endpoint-authz-user", "workspace.read"));
        assertThat(client.get("/api/authz-annotated-probe").status().value()).isEqualTo(200);
    }

    @Test
    void explicitPublicDeclarationStaysAnonymous() {
        ResponseEntity<String> response = anonymousGet("/api/authz-public-probe");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void authenticatedDeclarationRejectsAnonymousAndAcceptsAnyAuthenticatedSubject() {
        assertThat(anonymousGet("/api/authz-authenticated-probe").getStatusCode().value())
                .isEqualTo(401);

        authenticate(userJwt("endpoint-authz-user", "workspace.read"));
        assertThat(client.get("/api/authz-authenticated-probe").status().value()).isEqualTo(200);
    }

    @Test
    void classLevelDelegatedDeclarationCoversAllHandlers() {
        assertThat(anonymousGet("/api/authz-delegated-probe").getStatusCode().value())
                .isEqualTo(401);

        authenticate(userJwt("endpoint-authz-user", "workspace.read"));
        assertThat(client.get("/api/authz-delegated-probe").status().value()).isEqualTo(200);
        assertThat(client.postJson("/api/authz-delegated-probe", "{}").status().value())
                .isEqualTo(200);
    }

    @Test
    void configuredPublicPathEndpointIsUnaffected() {
        ResponseEntity<String> response = anonymousGet("/api/platform/info");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("AINER.COMMON.OK");
    }

    @Test
    void frameworkProvidedHandlersAreNotDeniedByEndpointGate() {
        // Actuator 健康检查：public-paths 登记 + 第三方 handler
        assertThat(anonymousGet("/actuator/health").getStatusCode().value()).isEqualTo(200);

        authenticate(userJwt("endpoint-authz-user", "workspace.read"));

        // 未匹配路径走 Spring Boot 错误分发（BasicErrorController），不能被 FAIL_CLOSED 改写成 403
        assertThat(client.get("/api/not-mapped-at-all").status().value()).isEqualTo(404);

        // springdoc 文档端点：ADR-0052 要求需要有效 JWT（外层链），而不是被端点门禁拒绝
        RestResponse openApi = client.get("/v3/api-docs");
        assertThat(openApi.status().value()).isEqualTo(200);
        assertThat(openApi.body()).contains("\"openapi\"");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AinerServerAuthorizationPolicyConfiguration.class,
            dev.ainer.authorization.AuthorizationModuleConfiguration.class,
            dev.ainer.server.endpoint.PlatformInfoController.class,
            EndpointAuthorizationProbes.UndeclaredProbeController.class,
            EndpointAuthorizationProbes.AnnotatedProbeController.class,
            EndpointAuthorizationProbes.PublicProbeController.class,
            EndpointAuthorizationProbes.AuthenticatedProbeController.class,
            EndpointAuthorizationProbes.DelegatedProbeController.class
    })
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.server.test-endpoint-authorization", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder endpointAuthorizationJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }
}
