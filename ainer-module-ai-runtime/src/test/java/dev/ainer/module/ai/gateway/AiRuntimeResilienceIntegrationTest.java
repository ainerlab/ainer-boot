package dev.ainer.module.ai.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.module.ai.gateway.application.AiGatewayApplicationService;
import dev.ainer.module.ai.gateway.application.AiIntermediateStateSweeper;
import dev.ainer.module.ai.gateway.application.AiInvocationRepository;
import dev.ainer.module.ai.gateway.application.AiTaskRepository;
import dev.ainer.module.ai.gateway.application.ModelProvider;
import dev.ainer.module.ai.gateway.domain.AiTaskRunStatus;
import dev.ainer.module.ai.gateway.infrastructure.openai.OpenAiCompatibleModelProvider;
import dev.ainer.web.request.RequestIds;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI runtime 韧性集成测试（真实 PostgreSQL 18.3 Testcontainers + 本地 HttpServer 桩）。
 *
 * <p>覆盖三类缺陷的端到端证据：
 * <ol>
 *   <li><b>无界阻塞</b>：上游「发完响应头就静默」时，非流式与流式调用都必须在有界时间内
 *       以 {@code AINER.AI.PROVIDER_TIMEOUT} 失败，并把 invocation 推进到 FAILED 终态；</li>
 *   <li><b>预算释放</b>：超时/自愈的调用按 {@code actual_cost = 0} 释放当日预算预占，
 *       后续请求不会被这条永远回不到终态的记录挡住；</li>
 *   <li><b>零自愈</b>：定时清扫把超期的 STARTED / RUNNING 中间态推进终态，且幂等、
 *       并发只处理一次；审计回写失败也不再掩盖原始失败原因。</li>
 * </ol>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AiRuntimeResilienceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.ai.enabled=true",
                "ainer.ai.provider.name=stub-provider",
                "ainer.ai.provider.api-key=stub-secret",
                "ainer.ai.provider.default-model=test/model",
                "ainer.ai.provider.allowed-models=test/model",
                "ainer.ai.provider.allow-insecure-http=true",
                "ainer.ai.provider.request-timeout=5s",
                // 总超时（覆盖响应体读取）刻意设小：桩静默时调用必须在几秒内失败
                "ainer.ai.provider.total-timeout=1s",
                "ainer.ai.provider.stream-total-timeout=1s",
                "ainer.ai.limits.requests-per-minute=10000",
                // 单次调用预估 0.128 USD：预算 0.20 允许一次、拒绝两次 —— 因此「第二次还能成功」
                // 就是「超时调用的预算预占确实被释放」的直接证据
                "ainer.ai.limits.subject-daily-budget=0.20",
                "ainer.ai.pricing.currency=USD",
                "ainer.ai.pricing.input-per-million-tokens=0",
                "ainer.ai.pricing.output-per-million-tokens=1000",
                // 自愈阈值必须大于 stream-total-timeout + 1m（配置校验强制）
                "ainer.ai.self-heal.enabled=true",
                "ainer.ai.self-heal.stuck-threshold=2m",
                "ainer.ai.self-heal.batch-size=200",
                // 真实调度：1s 一轮，scheduledSweepRunsWithoutManualTrigger 不手动触发任何清扫
                "ainer.ai.self-heal.scan-interval-ms=1000",
                // 门控本测试的嵌套测试 bean：模块的 @ComponentScan 会扫到 test-classes 下同包的
                // 嵌套测试配置，不门控就会与 AiGatewayModuleIntegrationTest 的测试 bean 撞名/串味
                "ainer.ai.test-resilience=true",
                "ainer.security.resource-server.enabled=true",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class AiRuntimeResilienceIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_ai_resilience")
            .withUsername("ainer")
            .withPassword("ainer");

    /** 供应商桩：真实 HTTP 端点，行为由每个用例替换的 handler 决定。 */
    private static final AtomicReference<HttpHandler> STUB_HANDLER = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicInteger STUB_REQUESTS =
            new java.util.concurrent.atomic.AtomicInteger();

    // 顺序很重要：executor 必须先于 server 初始化，否则 startStub() 里拿到的是 null，
    // setExecutor(null) 会让 JDK HttpServer 把 handler 跑在唯一的 dispatcher 线程上——
    // 一个「发完响应头就静默」的 handler 就会把整个桩服务器永久卡死。
    private static final ExecutorService STUB_EXECUTOR = Executors.newCachedThreadPool(
            runnable -> new Thread(runnable, "provider-stub-handler"));
    private static final HttpServer STUB = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                STUB_REQUESTS.incrementAndGet();
                STUB_HANDLER.get().handle(exchange);
            });
            server.setExecutor(STUB_EXECUTOR);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to start provider stub", exception);
        }
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
        STUB_EXECUTOR.shutdownNow();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ainer.ai.provider.base-url", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ModelProvider provider;

    @Autowired
    private AiGatewayApplicationService gatewayService;

    @Autowired
    private AiIntermediateStateSweeper sweeper;

    @Autowired
    private AiInvocationRepository invocationRepository;

    @Autowired
    private AiTaskRepository taskRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM ainer_ai_feedback");
        jdbcTemplate.update("DELETE FROM ainer_ai_result");
        jdbcTemplate.update("DELETE FROM ainer_ai_task_run");
        jdbcTemplate.update("DELETE FROM ainer_ai_context_snapshot");
        jdbcTemplate.update("DELETE FROM ainer_ai_task");
        jdbcTemplate.update("DELETE FROM ainer_ai_invocation");
        STUB_HANDLER.set(exchange -> respond(exchange, 200, "application/json", """
                {"id":"chatcmpl-ok","model":"test/model","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                """));
    }

    /** 正常完成的桩：上游故障/静默用例结束后复位用。 */
    private static void useCompletingStub() {
        STUB_HANDLER.set(exchange -> respond(exchange, 200, "application/json", """
                {"id":"chatcmpl-ok","model":"test/model","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                """));
    }

    @Test
    void usesTheRealOpenAiCompatibleProviderAgainstTheStub() {
        assertThat(provider).isInstanceOf(OpenAiCompatibleModelProvider.class);
    }

    /**
     * 交付物 1 的端到端断言：非流式调用在「发完响应头就静默」时必须有限失败，
     * invocation 进入 FAILED 终态，且超时调用的预算预占被释放 —— 同一 subject 的第二次调用
     * 仍然能通过预算闸门（否则会得到 429 BUDGET_EXCEEDED）。
     */
    @Test
    void nonStreamingCallFailsWithinBoundedTimeAndReleasesBudgetReservation() throws Exception {
        STUB_HANDLER.set(AiRuntimeResilienceIntegrationTest::respondHeadersThenGoSilent);

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post("/api/ai/chat/completions", "tenant-timeout", """
                {"model":"test/model","messages":[{"role":"USER","content":"hello"}],"maxOutputTokens":128}
                """);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.body()).contains("AINER.AI.PROVIDER_TIMEOUT");
        assertThat(elapsedMillis).isLessThan(20_000L);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || error_code FROM ainer_ai_invocation WHERE subject_id = 'tenant-timeout'",
                String.class)).isEqualTo("FAILED:AINER.AI.PROVIDER_TIMEOUT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost FROM ainer_ai_invocation WHERE subject_id = 'tenant-timeout'",
                BigDecimal.class)).isEqualByComparingTo("0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completed_at IS NOT NULL FROM ainer_ai_invocation WHERE subject_id = 'tenant-timeout'",
                Boolean.class)).isTrue();
        assertThat(dailyExposure("tenant-timeout")).isEqualByComparingTo("0");

        // 预算闸门对后续请求仍然开放：预占如果没有释放，这里会是 429 BUDGET_EXCEEDED
        useCompletingStub();
        HttpResponse<String> followUp = post("/api/ai/chat/completions", "tenant-timeout", """
                {"model":"test/model","messages":[{"role":"USER","content":"hello again"}],"maxOutputTokens":128}
                """);
        assertThat(followUp.statusCode())
                .as("stub requests=%d, body=%s", STUB_REQUESTS.get(), followUp.body())
                .isEqualTo(200);
        assertThat(followUp.body()).doesNotContain("BUDGET_EXCEEDED");
    }

    /** 交付物 1（流式路径）：SSE 上游静默时必须有界失败、写 FAILED 终态，并把 error 事件推给客户端。 */
    @Test
    void streamingCallFailsWithinBoundedTimeAndWritesTerminalAudit() throws Exception {
        STUB_HANDLER.set(AiRuntimeResilienceIntegrationTest::respondHeadersThenGoSilent);

        long startedNanos = System.nanoTime();
        HttpResponse<String> response = post("/api/ai/chat/completions/stream", "tenant-stream-timeout", """
                {"model":"test/model","messages":[{"role":"USER","content":"stream please"}],"maxOutputTokens":128}
                """);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("event:error", "AINER.AI.PROVIDER_TIMEOUT");
        assertThat(elapsedMillis).isLessThan(20_000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || error_code || ':' || streaming::text "
                        + "FROM ainer_ai_invocation WHERE subject_id = 'tenant-stream-timeout'",
                String.class)).isEqualTo("FAILED:AINER.AI.PROVIDER_TIMEOUT:true");
        assertThat(dailyExposure("tenant-stream-timeout")).isEqualByComparingTo("0");
    }

    /**
     * 既有口径回归：普通供应商失败（429 限流）仍按预估值占用当日预算，不因为本次改动被放开。
     * 见 docs/ai-gateway.md §4「避免并发或失败请求绕过上限」。
     */
    @Test
    void ordinaryProviderFailureKeepsTheConservativeBudgetExposure() throws Exception {
        STUB_HANDLER.set(exchange -> respond(exchange, 429, "application/json", "{\"error\":\"slow down\"}"));

        HttpResponse<String> response = post("/api/ai/chat/completions", "tenant-ordinary-failure", """
                {"model":"test/model","messages":[{"role":"USER","content":"hello"}],"maxOutputTokens":128}
                """);

        assertThat(response.statusCode())
                .as("stub requests=%d, body=%s", STUB_REQUESTS.get(), response.body())
                .isEqualTo(503);
        assertThat(response.body()).contains("AINER.AI.PROVIDER_RATE_LIMITED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost FROM ainer_ai_invocation WHERE subject_id = 'tenant-ordinary-failure'",
                BigDecimal.class)).isNull();
        assertThat(dailyExposure("tenant-ordinary-failure")).isEqualByComparingTo("0.128");
    }

    /**
     * 交付物 2（真调度）：不手动调用清扫方法，只插入超期 STARTED 行，等待 {@code @Scheduled} 驱动的
     * 清扫自己把它推进终态。这条测试专门防「@EnableScheduling 不生效导致自愈永不运行」的回归。
     */
    @Test
    void scheduledSweepRunsWithoutManualTrigger() throws InterruptedException {
        UUID stale = insertStartedInvocation("tenant-scheduled", Instant.now().minus(Duration.ofMinutes(10)));
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String status = null;
        while (System.nanoTime() < deadlineNanos) {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM ainer_ai_invocation WHERE id = ?::uuid", String.class, stale.toString());
            if ("FAILED".equals(status)) {
                break;
            }
            Thread.sleep(100L);
        }

        assertThat(status).as("定时清扫未在 30s 内自动把超期 STARTED 行推进终态").isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_code FROM ainer_ai_invocation WHERE id = ?::uuid", String.class, stale.toString()))
                .isEqualTo("AINER.AI.INVOCATION_SELF_HEALED");
        assertThat(dailyExposure("tenant-scheduled")).isEqualByComparingTo("0");
    }

    /** 交付物 2：超期 STARTED 被清扫为终态并释放预算占用；未超期的 STARTED 不受影响；重复清扫幂等。 */
    @Test
    void scheduledSweepHealsStaleStartedInvocationAndReleasesBudget() {
        UUID stale = insertStartedInvocation("tenant-self-heal", Instant.now().minus(Duration.ofMinutes(10)));
        // 30s 前开始、未超过 2m 阈值：既证明「未超期不误伤」，也让最老中间态年龄可观测（非 0）
        UUID fresh = insertStartedInvocation("tenant-self-heal", Instant.now().minus(Duration.ofSeconds(30)));
        BigDecimal exposureBeforeSweep = dailyExposure("tenant-self-heal");
        double healedBefore = healedCounter("invocation");

        sweeper.sweepIntermediateStates();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || error_code FROM ainer_ai_invocation WHERE id = ?::uuid",
                String.class, stale.toString()))
                .isEqualTo("FAILED:AINER.AI.INVOCATION_SELF_HEALED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actual_cost FROM ainer_ai_invocation WHERE id = ?::uuid",
                BigDecimal.class, stale.toString())).isEqualByComparingTo("0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT latency_ms > 0 AND completed_at IS NOT NULL FROM ainer_ai_invocation WHERE id = ?::uuid",
                Boolean.class, stale.toString())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_ai_invocation WHERE id = ?::uuid",
                String.class, fresh.toString())).isEqualTo("STARTED");
        // 释放：超期行的 0.128 预占被释放，只剩未超期那一行仍在占用
        assertThat(exposureBeforeSweep).isEqualByComparingTo("0.256");
        assertThat(dailyExposure("tenant-self-heal")).isEqualByComparingTo("0.128");
        assertThat(healedCounter("invocation")).isEqualTo(healedBefore + 1);

        // 幂等：再扫一次不产生任何新增终态写入
        double healedAfterFirstSweep = healedCounter("invocation");
        sweeper.sweepIntermediateStates();
        assertThat(healedCounter("invocation")).isEqualTo(healedAfterFirstSweep);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation WHERE status = 'FAILED' "
                        + "AND error_code = 'AINER.AI.INVOCATION_SELF_HEALED'",
                Integer.class)).isEqualTo(1);

        // 指标：最老中间态年龄可被 Prometheus 读取（> 0 说明确实观察到中间态）
        assertThat(meterRegistry.get("ainer.ai.intermediate_state.oldest_age_seconds")
                .tag("state", "invocation").gauge().value()).isGreaterThan(0d);
        assertThat(meterRegistry.get("ainer.ai.intermediate_state.stuck")
                .tag("state", "invocation").gauge().value()).isZero();
    }

    /** 交付物 2（并发语义）：两个清扫实例同时扫，每一行只被处理一次。 */
    @Test
    void concurrentSweepsHealEachStuckRowExactlyOnce() throws Exception {
        int rows = 24;
        for (int index = 0; index < rows; index++) {
            insertStartedInvocation("tenant-concurrent", Instant.now().minus(Duration.ofMinutes(10)));
        }
        double healedBefore = healedCounter("invocation");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<?>> futures = List.of(
                    executors.submit(() -> sweepAfterBarrier(barrier)),
                    executors.submit(() -> sweepAfterBarrier(barrier)));
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executors.shutdownNow();
        }

        // 两轮清扫的自愈计数之和必须恰好等于行数：CAS + SKIP LOCKED 保证没有一行被处理两次
        assertThat(healedCounter("invocation") - healedBefore).isEqualTo(rows);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation "
                        + "WHERE status = 'FAILED' AND error_code = 'AINER.AI.INVOCATION_SELF_HEALED'",
                Integer.class)).isEqualTo(rows);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation WHERE status = 'STARTED'",
                Integer.class)).isZero();
    }

    /** 交付物 2：AiTask / AiTaskRun 的 RUNNING 中间态同样纳入自愈，且回写带期望态（CAS）。 */
    @Test
    void sweepHealsStuckRunningTaskAndRunThenRejectsLateWriteBack() {
        Instant stale = Instant.now().minus(Duration.ofMinutes(10));
        UUID taskId = Uuidv7.generate();
        UUID runId = Uuidv7.generate();
        UUID snapshotId = Uuidv7.generate();
        jdbcTemplate.update("""
                INSERT INTO ainer_ai_context_snapshot
                    (id, evidence_refs, memory_refs, as_of, schema_version, created_at)
                VALUES (?::uuid, '[]'::jsonb, '[]'::jsonb, ?, 1, ?)
                """, snapshotId.toString(), java.sql.Timestamp.from(stale), java.sql.Timestamp.from(stale));
        jdbcTemplate.update("""
                INSERT INTO ainer_ai_task
                    (id, task_type, status, trigger, triggered_by, created_at, updated_at)
                VALUES (?::uuid, 'identity-weekly-report', 'RUNNING', 'manual', 'tester', ?, ?)
                """, taskId.toString(), java.sql.Timestamp.from(stale), java.sql.Timestamp.from(stale));
        jdbcTemplate.update("""
                INSERT INTO ainer_ai_task_run
                    (id, task_id, context_snapshot_id, governed_context, status, started_at, completed_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, '{}'::jsonb, 'RUNNING', ?, NULL)
                """, runId.toString(), taskId.toString(), snapshotId.toString(),
                java.sql.Timestamp.from(stale));

        sweeper.sweepIntermediateStates();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_ai_task_run WHERE id = ?::uuid", String.class, runId.toString()))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_ai_task WHERE id = ?::uuid", String.class, taskId.toString()))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completed_at IS NOT NULL FROM ainer_ai_task_run WHERE id = ?::uuid",
                Boolean.class, runId.toString())).isTrue();

        // 迟到的写回（自愈已写终态）必须被期望态挡住，不能把 FAILED 覆盖成 COMPLETED
        assertThat(taskRepository.updateTaskRunStatus(
                runId, AiTaskRunStatus.RUNNING, AiTaskRunStatus.COMPLETED, Instant.now())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ainer_ai_task_run WHERE id = ?::uuid", String.class, runId.toString()))
                .isEqualTo("FAILED");
    }

    /**
     * 交付物 3：审计回写失败（STARTED 行不存在）不得替换原始失败原因。
     *
     * <p>做法：调用在途（桩静默 1s 超时）时删掉 STARTED 行，随后审计回写必然抛
     * {@code IllegalStateException}；响应仍必须是原始的 504 + {@code AINER.AI.PROVIDER_TIMEOUT}，
     * 而不是被审计异常替换成 500。
     */
    @Test
    void auditFailureDoesNotMaskTheOriginalProviderFailure() throws Exception {
        STUB_HANDLER.set(AiRuntimeResilienceIntegrationTest::respondHeadersThenGoSilent);
        ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<HttpResponse<String>> inFlight = executors.submit(() -> post(
                    "/api/ai/chat/completions", "tenant-masking", """
                            {"model":"test/model","messages":[{"role":"USER","content":"hello"}],"maxOutputTokens":128}
                            """));

            awaitStartedInvocation("tenant-masking");
            jdbcTemplate.update("DELETE FROM ainer_ai_invocation WHERE subject_id = 'tenant-masking'");

            HttpResponse<String> response = inFlight.get(60, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(504);
            assertThat(response.body())
                    .contains("AINER.AI.PROVIDER_TIMEOUT")
                    .doesNotContain("AINER.COMMON.INTERNAL_ERROR");
        } finally {
            executors.shutdownNow();
        }
    }

    private void sweepAfterBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("barrier wait failed", exception);
        }
        sweeper.sweepIntermediateStates();
    }

    private double healedCounter(String state) {
        return meterRegistry.get("ainer.ai.intermediate_state.healed").tag("state", state).counter().count();
    }

    private BigDecimal dailyExposure(String subjectId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(COALESCE(actual_cost, estimated_cost)), 0)
                FROM ainer_ai_invocation
                WHERE subject_id = ?
                  AND status IN ('STARTED', 'SUCCEEDED', 'FAILED')
                """, BigDecimal.class, subjectId);
    }

    private UUID insertStartedInvocation(String subjectId, Instant startedAt) {
        UUID id = Uuidv7.generate();
        jdbcTemplate.update("""
                INSERT INTO ainer_ai_invocation (
                    id, subject_id, request_id, provider, requested_model, resolved_model,
                    streaming, status, policy_decision, prompt_fingerprint,
                    usage_estimated, estimated_cost, currency, started_at
                ) VALUES (?::uuid, ?, 'request:self-heal', 'stub-provider', 'test/model', 'test/model',
                          false, 'STARTED', 'ALLOWED', ?, false, 0.128, 'USD', ?)
                """, id.toString(), subjectId, "a".repeat(64), java.sql.Timestamp.from(startedAt));
        return id;
    }

    private void awaitStartedInvocation(String subjectId) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Integer started = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ainer_ai_invocation WHERE subject_id = ? AND status = 'STARTED'",
                    Integer.class, subjectId);
            if (started != null && started > 0) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new IllegalStateException("invocation for " + subjectId + " never reached STARTED");
    }

    private HttpResponse<String> post(String path, String subjectId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + userJwt(subjectId))
                .header(RequestIds.HEADER, "request-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String userJwt(String subjectId) {
        return dev.ainer.testsupport.jwt.JwtTestSupport.signUserJwt(
                HTTP_RSA_JWK, "https://auth.ainer.test", "ainer-api", subjectId, "ai.invoke");
    }

    static final com.nimbusds.jose.jwk.RSAKey HTTP_RSA_JWK =
            dev.ainer.testsupport.jwt.JwtTestSupport.generateRsaKey();

    /**
     * 发完响应头 + 一小段正文后静默。
     *
     * <p>必须先写出正文：{@code com.sun.net.httpserver.HttpServer} 在 chunked 响应下要等到第一次写
     * 正文才把响应头刷到 socket（实测 headers-only 时客户端 60s 拿不到响应头）。这也正是缺陷形态——
     * 客户端一旦拿到响应头，{@code HttpRequest.timeout} 就不再覆盖响应体读取（JDK-8258397）。
     */
    private static void respondHeadersThenGoSilent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(("data: {\"id\":\"chatcmpl-silent\",\"model\":\"test/model\","
                + "\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}\n\n")
                .getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        try {
            Thread.sleep(60_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AiRuntimeModuleConfiguration.class, StubConfiguration.class})
    static class TestApplication {
    }

    /**
     * 只在 {@code ainer.ai.test-resilience=true} 时生效：模块的 {@code @ComponentScan} 会把
     * test-classes 里同包的嵌套测试配置一起扫进来，门控避免与其它集成测试的测试 bean 互相污染。
     */
    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ainer.ai.test-resilience", havingValue = "true")
    static class StubConfiguration {

        /** 真链：RSA 验签 + issuer 校验（JwtTestSupport），身份来自已验证 token。 */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return dev.ainer.testsupport.jwt.JwtTestSupport.jwtDecoder(
                    HTTP_RSA_JWK, "https://auth.ainer.test", "ainer-api");
        }

        /** 让自愈指标在测试里真的被注册（生产由 actuator 提供 MeterRegistry）。 */
        @Bean
        @Primary
        MeterRegistry testMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
