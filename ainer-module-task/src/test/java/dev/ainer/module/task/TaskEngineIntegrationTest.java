package dev.ainer.module.task;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.module.task.tasks.infrastructure.TaskExecutionEngine;
import dev.ainer.module.task.tasks.infrastructure.TaskExecutionEngine.TaskHandler;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import dev.ainer.testsupport.rest.RestResponse;
import dev.ainer.testsupport.rest.RestTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 任务执行引擎真实 PostgreSQL 集成测试（ADR-0047 §3）：成功、失败退避重试、无处理器
 * 耗尽、周期重置、超时看门狗与僵尸清扫，以及领取/生命周期审计断言。
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.task.enabled=true",
                "ainer.task.engine.enabled=true",
                "ainer.task.engine.poll-interval-ms=100",
                "ainer.task.engine.batch-size=10",
                "ainer.task.engine.retry-base-ms=1000",
                "ainer.task.engine.retry-max-ms=1000",
                "ainer.task.engine.zombie-cutoff-multiplier=2",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off",
                "ainer.task.test-engine-http=true",
                "ainer.security.resource-server.enabled=true"
        })
@AutoConfigureTestRestTemplate
class TaskEngineIntegrationTest {

    private static final String ISSUER = "https://auth.ainer.test";
    private static final String AUDIENCE = "ainer-api";
    private static final RSAKey RSA_JWK = JwtTestSupport.generateRsaKey();
    private static final Duration WAIT = Duration.ofSeconds(15);

    static final AtomicInteger OK_COUNT = new AtomicInteger();
    static final AtomicInteger FLAKY_COUNT = new AtomicInteger();
    static final AtomicInteger TICK_COUNT = new AtomicInteger();
    static final AtomicInteger SLOW_COUNT = new AtomicInteger();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.3-alpine"))
                    .withDatabaseName("ainer_task_engine_test")
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
    org.springframework.boot.resttestclient.TestRestTemplate restTemplate;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private RestTestClient client;

    @BeforeEach
    void cleanAndAuthenticate() {
        jdbcTemplate.execute("DELETE FROM ainer_task_audit");
        jdbcTemplate.execute("DELETE FROM ainer_task_job");
        jdbcTemplate.execute("DELETE FROM ainer_task_definition");
        OK_COUNT.set(0);
        FLAKY_COUNT.set(0);
        TICK_COUNT.set(0);
        SLOW_COUNT.set(0);
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
                request.getHeaders().setBearerAuth(JwtTestSupport.signServiceJwt(
                        RSA_JWK, ISSUER, AUDIENCE, "task-admin",
                        "task.read task.manage task.submit"));
            }
            return execution.execute(request, body);
        });
        client = RestTestClient.forLocalServer(restTemplate, port);
    }

    // ------------------------------------------------------------------ 断言辅助

    private String submit(String taskType, String body) {
        RestResponse submitted = client.postJson("/api/tasks/jobs", body.formatted(taskType));
        assertThat(submitted.status().value()).isEqualTo(201);
        return (String) submitted.jsonPath("$.data.id");
    }

    private String registerDefinition(String taskType, int maxAttempts, int timeoutSeconds) {
        RestResponse created = client.postJson("/api/tasks/definitions", """
                {"taskType": "%s", "displayName": "引擎测试", "handlerRef": "engine-test",
                 "maxAttempts": %d, "timeoutSeconds": %d}
                """.formatted(taskType, maxAttempts, timeoutSeconds));
        assertThat(created.status().value()).isEqualTo(201);
        return (String) created.jsonPath("$.data.id");
    }

    /** 轮询等待数据库行条件成立，超时 fail 并输出当前状态。 */
    private void awaitRow(String sql, Class<?> type, Object expected, Object... args) {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            List<?> values = jdbcTemplate.queryForList(sql, type, args);
            if (!values.isEmpty() && expected.toString().equals(String.valueOf(values.get(0)))) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("等待超时: %s 期望 %s，实际 %s".formatted(sql, expected,
                jdbcTemplate.queryForList(sql, type, args)));
    }

    private List<String> auditEvents(UUID jobId) {
        return jdbcTemplate.queryForList(
                "SELECT event FROM ainer_task_audit WHERE job_id = ? ORDER BY occurred_at, id",
                String.class, jobId);
    }

    record JobRow(String status, Integer attemptCount, String lastError) {
    }

    private JobRow job(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT status, attempt_count, last_error FROM ainer_task_job WHERE id = ?",
                (rs, n) -> new JobRow(rs.getString(1), rs.getInt(2), rs.getString(3)), id);
    }

    // ------------------------------------------------------------------ 用例

    @Test
    void successJobWritesClaimedAndSucceededAudits() {
        registerDefinition("engine.ok", 3, 60);
        UUID jobId = UUID.fromString(submit("engine.ok", """
                {"taskType": "%s", "payload": "{\\"n\\":1}"}
                """));

        awaitRow("SELECT status FROM ainer_task_job WHERE id = ?",
                String.class, "SUCCEEDED", jobId);

        JobRow row = job(jobId);
        assertThat(row.attemptCount()).isEqualTo(1);
        assertThat(row.lastError()).isNull();
        assertThat(auditEvents(jobId))
                .containsExactly("SUBMITTED", "CLAIMED", "SUCCEEDED");
    }

    @Test
    void transientFailureRetriesWithBackoffThenSucceeds() {
        registerDefinition("engine.flaky", 3, 60);
        UUID jobId = UUID.fromString(submit("engine.flaky", """
                {"taskType": "%s"}
                """));

        awaitRow("SELECT status FROM ainer_task_job WHERE id = ?",
                String.class, "SUCCEEDED", jobId);

        JobRow row = job(jobId);
        assertThat(row.attemptCount()).isEqualTo(2);
        List<String> events = auditEvents(jobId);
        assertThat(events).contains("SUBMITTED", "CLAIMED", "RETRY_SCHEDULED", "SUCCEEDED");
        assertThat(events.indexOf("RETRY_SCHEDULED")).isLessThan(events.indexOf("SUCCEEDED"));
    }

    @Test
    void missingHandlerExhaustsAsTerminal() {
        registerDefinition("engine.orphan", 2, 60);
        UUID jobId = UUID.fromString(submit("engine.orphan", """
                {"taskType": "%s"}
                """));

        awaitRow("SELECT status FROM ainer_task_job WHERE id = ?",
                String.class, "EXHAUSTED", jobId);

        JobRow row = job(jobId);
        assertThat(row.attemptCount()).isEqualTo(2);
        assertThat(row.lastError()).contains("no handler registered");
        assertThat(auditEvents(jobId)).contains("SUBMITTED", "CLAIMED", "EXHAUSTED");
    }

    @Test
    void periodicJobReschedulesAfterSuccess() {
        registerDefinition("engine.tick", 5, 60);
        UUID jobId = UUID.fromString(submit("engine.tick", """
                {"taskType": "%s", "intervalSeconds": 1}
                """));

        Instant deadline = Instant.now().plus(WAIT);
        while (TICK_COUNT.get() < 2 && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(TICK_COUNT.get()).as("周期任务至少执行两次").isGreaterThanOrEqualTo(2);
        String status = (String) jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_task_job WHERE id = ?", String.class, jobId);
        assertThat(status).isIn("PENDING", "RUNNING");
    }

    @Test
    void timeoutWatchdogFailsSlowJobAndDiscardsLateResult() {
        registerDefinition("engine.slow", 1, 1);
        UUID jobId = UUID.fromString(submit("engine.slow", """
                {"taskType": "%s"}
                """));

        awaitRow("SELECT status FROM ainer_task_job WHERE id = ?",
                String.class, "EXHAUSTED", jobId);

        JobRow row = job(jobId);
        assertThat(row.lastError()).contains("timeout");
        // 不杀线程：迟到的执行线程自然结束后结果被丢弃，不再产生第二次执行计数
        awaitCondition(() -> SLOW_COUNT.get() == 1);
        assertThat(auditEvents(jobId)).contains("EXHAUSTED");
    }

    @Test
    void zombieRunningIsSweptBackToPending() {
        registerDefinition("engine.zombie", 3, 60);
        jdbcTemplate.update("""
                INSERT INTO ainer_task_job
                    (task_type, status, attempt_count, max_attempts, next_run_at,
                     locked_by, locked_at, created_by_issuer, created_by_type,
                     created_by_id, created_at, updated_at)
                VALUES ('engine.zombie', 'RUNNING', 1, 3,
                        now() + interval '1 hour',
                        'ghost-instance', now() - interval '3 hours',
                        'system', 'SERVICE', 'ghost', now(), now())
                """);

        awaitRow("SELECT status FROM ainer_task_job WHERE locked_by IS NULL "
                        + "AND task_type = 'engine.zombie'",
                String.class, "PENDING");
        Integer locked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_task_job WHERE status = 'RUNNING'",
                Integer.class);
        assertThat(locked).isZero();
    }

    private void awaitCondition(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                fail("等待条件超时");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ------------------------------------------------------------------ 处理器与装配

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TaskModuleConfiguration.class)
    static class TestApplication {

        @Bean
        TaskHandler okHandler() {
            return new TaskHandler() {
                @Override
                public String taskType() {
                    return "engine.ok";
                }

                @Override
                public void execute(String payloadJson) {
                    OK_COUNT.incrementAndGet();
                }
            };
        }

        @Bean
        TaskHandler flakyHandler() {
            return new TaskHandler() {
                @Override
                public String taskType() {
                    return "engine.flaky";
                }

                @Override
                public void execute(String payloadJson) {
                    if (FLAKY_COUNT.incrementAndGet() <= 1) {
                        throw new IllegalStateException("boom-once");
                    }
                }
            };
        }

        @Bean
        TaskHandler tickHandler() {
            return new TaskHandler() {
                @Override
                public String taskType() {
                    return "engine.tick";
                }

                @Override
                public void execute(String payloadJson) {
                    TICK_COUNT.incrementAndGet();
                }
            };
        }

        @Bean
        TaskHandler slowHandler() {
            return new TaskHandler() {
                @Override
                public String taskType() {
                    return "engine.slow";
                }

                @Override
                public void execute(String payloadJson) throws InterruptedException {
                    SLOW_COUNT.incrementAndGet();
                    Thread.sleep(3000);
                }
            };
        }

        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                name = "ainer.task.test-engine-http", havingValue = "true")
        @Bean
        @Primary
        JwtDecoder engineTestJwtDecoder() {
            return JwtTestSupport.jwtDecoder(RSA_JWK, ISSUER, AUDIENCE);
        }
    }
}
