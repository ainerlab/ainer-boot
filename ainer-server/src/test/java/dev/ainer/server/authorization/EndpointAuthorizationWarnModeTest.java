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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ainer.security.endpoint-authorization.mode=warn} 的升级期灰度语义。
 *
 * <p>WARN 只改变「未声明端点」的处置：放行但必须留 WARN 日志（不再是静默放行）。
 * 已声明的端点一律照常执行——{@code @AinerAuthorize} 仍走决策引擎（无 Binding 403），
 * {@code @EndpointAccess(PUBLIC)} 仍匿名可达。默认值仍是 {@code fail-closed}，
 * 本模式只作为宿主显式配置的过渡态存在（docs/security.md §3.4）。
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        classes = EndpointAuthorizationWarnModeTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.authorization.trusted-managers=https://auth.ainer.test|platform-ops",
                "ainer.security.resource-server.enabled=true",
                "ainer.security.endpoint-authorization.mode=warn",
                "ainer.security.resource-server.public-paths[0]=/api/platform/info",
                "ainer.security.resource-server.public-paths[1]=/actuator/health/**",
                "ainer.security.resource-server.public-paths[2]=/api/authz-public-probe",
                "ainer.server.test-endpoint-authorization-warn=true"
        })
@AutoConfigureTestRestTemplate
class EndpointAuthorizationWarnModeTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_endpoint_authz_warn_test")
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

    private ResponseEntity<String> anonymousGet(String path) {
        restTemplate.getRestTemplate().setInterceptors(List.of());
        return restTemplate.getForEntity(baseUrl + path, String.class);
    }

    @Test
    void undeclaredEndpointIsWarnedAndAllowedForAuthenticatedSubject(CapturedOutput output) {
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "endpoint-authz-user", "workspace.read"));

        RestResponse response = client.get("/api/authz-undeclared-probe");

        // WARN 模式保留旧行为（只要求已认证），但放行必须留痕、可告警
        assertThat(response.status().value()).isEqualTo(200);
        assertThat(output.getOut())
                .contains("端点未声明授权口径，WARN 模式放行")
                .contains("/api/authz-undeclared-probe")
                .contains("EndpointAuthorizationProbes$UndeclaredProbeController#peek");
    }

    @Test
    void undeclaredEndpointStillRequiresAuthentication() {
        assertThat(anonymousGet("/api/authz-undeclared-probe").getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void declarationsStillApplyInWarnMode() {
        // @AinerAuthorize 不受模式影响：无 Binding 仍 403
        authenticate(JwtTestSupport.signUserJwt(
                RSA_JWK, ISSUER, AUDIENCE, "endpoint-authz-user", "workspace.read"));
        assertThat(client.get("/api/authz-annotated-probe").status().value()).isEqualTo(403);

        // @EndpointAccess(PUBLIC) 不受模式影响：匿名仍 200
        assertThat(anonymousGet("/api/authz-public-probe").getStatusCode().value()).isEqualTo(200);
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
                name = "ainer.server.test-endpoint-authorization-warn", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder endpointAuthorizationWarnJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }
}
