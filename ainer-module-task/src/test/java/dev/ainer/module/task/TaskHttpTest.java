package dev.ainer.module.task;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务管理 API 真 JWT HTTP 测试（ADR-0047）：注册 → 提交 → 分页 → 取消 → 重试 →
 * 审计断言。执行引擎在测试中关闭（只验管理面）。
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.task.enabled=true",
                "ainer.task.engine.enabled=false",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.task.test-http=true"
        })
@AutoConfigureTestRestTemplate
class TaskHttpTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_task_http_test")
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

    @BeforeEach
    void cleanAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM ainer_task_audit");
        jdbcTemplate.execute("DELETE FROM ainer_task_job");
        jdbcTemplate.execute("DELETE FROM ainer_task_definition");
        client = RestTestClient.forLocalServer(restTemplate, port);
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "task-admin",
                "task.read task.manage task.submit"));
    }

    private void authenticate(String jwt) {
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(jwt);
            }
            return execution.execute(request, body);
        });
    }

    private String registerDefinition(String taskType) {
        RestResponse created = client.postJson("/api/tasks/definitions", """
                {"taskType": "%s", "displayName": "测试任务", "handlerRef": "testHandler",
                 "maxAttempts": 3, "timeoutSeconds": 60}
                """.formatted(taskType));
        assertThat(created.status().value()).isEqualTo(201);
        return (String) created.jsonPath("$.data.id");
    }

    @Test
    void missingTokenIsUnauthorized() {
        restTemplate.getRestTemplate().setInterceptors(java.util.List.of());
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/tasks/jobs", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void registerSubmitAndQueryJob() {
        registerDefinition("test.report");

        RestResponse submitted = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.report", "payload": "{\\"type\\":\\"daily\\"}",
                 "delaySeconds": 60}
                """);
        assertThat(submitted.status().value()).isEqualTo(201);
        String jobId = (String) submitted.jsonPath("$.data.id");
        assertThat((Integer) submitted.jsonPath("$.data.attemptCount")).isEqualTo(0);
        assertThat((String) submitted.jsonPath("$.data.status")).isEqualTo("PENDING");

        RestResponse fetched = client.get("/api/tasks/jobs/" + jobId);
        assertThat(fetched.status().value()).isEqualTo(200);

        RestResponse listed = client.get("/api/tasks/jobs?status=PENDING");
        assertThat(listed.status().value()).isEqualTo(200);
        assertThat(listed.jsonPath("$.data.total")).isNotNull();

        // 审计行
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_task_audit WHERE job_id = ?",
                Integer.class, UUID.fromString(jobId));
        assertThat(audits).isEqualTo(1); // SUBMITTED
    }

    @Test
    void duplicateTaskTypeIsConflict() {
        registerDefinition("test.dup");
        RestResponse duplicate = client.postJson("/api/tasks/definitions", """
                {"taskType": "test.dup", "displayName": "重复", "handlerRef": "h",
                 "maxAttempts": 3, "timeoutSeconds": 60}
                """);
        assertThat(duplicate.status().value()).isEqualTo(409);
        assertThat(duplicate.jsonPath("$.code")).isEqualTo("AINER.TASK.DUPLICATE_TASK_TYPE");
    }

    @Test
    void cancelPendingJob() {
        registerDefinition("test.cancel");
        RestResponse submitted = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.cancel", "payload": "{}", "delaySeconds": 3600}
                """);
        String jobId = (String) submitted.jsonPath("$.data.id");

        RestResponse cancelled = client.postJson(
                "/api/tasks/jobs/" + jobId + "/cancellations", "{}");
        assertThat(cancelled.status().value()).isEqualTo(200);
        assertThat(cancelled.jsonPath("$.data.status")).isEqualTo("CANCELLED");

        // 已取消不能再次取消
        RestResponse again = client.postJson(
                "/api/tasks/jobs/" + jobId + "/cancellations", "{}");
        assertThat(again.status().value()).isEqualTo(409);
    }

    @Test
    void retryFailedJob() {
        registerDefinition("test.retry");
        // 直接 DB 模拟 FAILED 状态
        RestResponse submitted = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.retry", "payload": "{}", "delaySeconds": 0}
                """);
        String jobId = (String) submitted.jsonPath("$.data.id");
        jdbcTemplate.update(
                "UPDATE ainer_task_job SET status='FAILED', last_error='simulated' WHERE id=?",
                UUID.fromString(jobId));

        RestResponse retried = client.postJson(
                "/api/tasks/jobs/" + jobId + "/retries", "{}");
        assertThat(retried.status().value()).isEqualTo(200);
        assertThat(retried.jsonPath("$.data.status")).isEqualTo("PENDING");
    }

    @Test
    void pausedDefinitionRejectsSubmission() {
        registerDefinition("test.paused");
        RestResponse paused = client.postJson(
                "/api/tasks/definitions/test.paused/status-changes",
                "{\"status\": \"PAUSED\"}");
        assertThat(paused.status().value()).isEqualTo(200);
        assertThat(paused.jsonPath("$.data.status")).isEqualTo("PAUSED");

        RestResponse submitted = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.paused", "payload": "{}"}
                """);
        assertThat(submitted.status().value()).isEqualTo(409);
        assertThat(submitted.jsonPath("$.code")).isEqualTo("AINER.TASK.DEFINITION_PAUSED");
    }

    @Test
    void scopeEnforcement() {
        authenticate(JwtTestSupport.signServiceJwt(
                RSA_JWK, ISSUER, AUDIENCE, "task-reader", "task.read"));
        RestResponse response = client.postJson("/api/tasks/definitions", """
                {"taskType": "test.noscope", "displayName": "x", "handlerRef": "h",
                 "maxAttempts": 3, "timeoutSeconds": 60}
                """);
        assertThat(response.status().value()).isEqualTo(403);
    }

    @Test
    void invalidStatusValueIsRejected() {
        registerDefinition("test.status");
        // 未知取值不允许被静默解释为恢复
        RestResponse rejected = client.postJson(
                "/api/tasks/definitions/test.status/status-changes",
                "{\"status\": \"PAUSE\"}");
        assertThat(rejected.status().value()).isEqualTo(422);
        assertThat(rejected.jsonPath("$.code")).isEqualTo("AINER.TASK.INVALID_STATUS");
        // 原定义不受影响，仍为 ACTIVE
        RestResponse submitted = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.status", "payload": "{}"}
                """);
        assertThat(submitted.status().value()).isEqualTo(201);
    }

    @Test
    void malformedJsonPayloadIsRejected() {
        registerDefinition("test.payload");
        // 首尾字符形似对象但不是合法 JSON——必须 422 而非穿透为 500
        RestResponse rejected = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.payload", "payload": "{not valid json}"}
                """);
        assertThat(rejected.status().value()).isEqualTo(422);
        assertThat(rejected.jsonPath("$.code")).isEqualTo("AINER.TASK.INVALID_PAYLOAD");

        RestResponse nonObject = client.postJson("/api/tasks/jobs", """
                {"taskType": "test.payload", "payload": "[1,2]"}
                """);
        assertThat(nonObject.status().value()).isEqualTo(422);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TaskModuleConfiguration.class)
    static class TestApplication {

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.task.test-http", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder taskTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }
}
