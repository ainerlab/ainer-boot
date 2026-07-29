package dev.ainer.module.ai.gateway;

import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.module.ai.gateway.application.AiInvocationAuditService;
import dev.ainer.module.ai.gateway.application.ModelProvider;
import dev.ainer.module.ai.gateway.application.ModelStreamObserver;
import dev.ainer.module.ai.gateway.application.ProviderFailure;
import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import dev.ainer.web.request.RequestIds;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AiGatewayModuleIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ainer.ai.enabled=true",
                "ainer.ai.provider.name=test-provider",
                "ainer.ai.provider.base-url=http://localhost:9",
                "ainer.ai.provider.api-key=test-secret",
                "ainer.ai.provider.default-model=test/model",
                "ainer.ai.provider.allowed-models=test/model",
                "ainer.ai.provider.allow-insecure-http=true",
                "ainer.ai.provider.request-timeout=5s",
                "ainer.ai.limits.requests-per-minute=10000",
                "ainer.ai.limits.tenant-daily-budget=0.01",
                "ainer.ai.pricing.currency=USD",
                "ainer.ai.pricing.input-per-million-tokens=1.00",
                "ainer.ai.pricing.output-per-million-tokens=2.00",
                "ainer.security.resource-server.enabled=true",
                "mybatis.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class AiGatewayModuleIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_ai_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestModelProvider provider;

    @Autowired
    private AiInvocationAuditService auditService;

    @Autowired
    private Clock clock;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_ai_invocation");
        provider.reset();
    }

    @Test
    void migrationCreatesAuditSchemaWithoutPromptOrResponseColumns() {
        assertThat(flyway.info().applied()).hasSize(2);
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'ainer_ai_invocation'",
                String.class);

        assertThat(columns)
                .contains("prompt_fingerprint", "input_tokens", "actual_cost", "policy_decision")
                .doesNotContain("prompt", "messages", "response", "content");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void completionReturnsUsageCostAndPersistsTenantAudit() throws Exception {
        HttpResponse<String> completion = post("/api/ai/chat/completions", "tenant-a", """
                {"model":"test/model","messages":[{"role":"USER","content":"hello ainer"}],"maxOutputTokens":128,"temperature":0.2}
                """);

        assertThat(completion.statusCode()).isEqualTo(200);
        JsonNode response = objectMapper.readTree(completion.body());
        String invocationId = response.path("data").path("invocationId").stringValue();
        assertThat(response.path("data").path("content").stringValue()).isEqualTo("Ainer answer");
        assertThat(response.path("data").path("usage").path("totalTokens").intValue()).isEqualTo(18);
        assertThat(response.path("data").path("cost").path("amount").decimalValue())
                .isEqualByComparingTo("0.00002600");

        HttpResponse<String> audit = get("/api/ai/invocations/" + invocationId, "tenant-a");
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(audit.body())
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"providerRequestId\":\"provider-request-1\"")
                .doesNotContain("hello ainer", "Ainer answer");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT char_length(prompt_fingerprint) FROM ainer_ai_invocation WHERE id = ?::uuid",
                Integer.class,
                invocationId)).isEqualTo(64);
    }

    @Test
    void rejectsSensitiveDataBeforeProviderAndAuditsPolicyDecision() throws Exception {
        HttpResponse<String> response = post("/api/ai/chat/completions", "tenant-sensitive", """
                {"messages":[{"role":"USER","content":"my key is sk-1234567890abcdefghijklmnop"}]}
                """);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("AINER.AI.SENSITIVE_DATA_REJECTED");
        assertThat(provider.calls()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || policy_decision FROM ainer_ai_invocation",
                String.class)).isEqualTo("REJECTED:REJECTED_SENSITIVE_DATA");
    }

    @Test
    void rejectsRequestWhenEstimatedCostExceedsDailyBudget() throws Exception {
        HttpResponse<String> response = post("/api/ai/chat/completions", "tenant-budget", """
                {"messages":[{"role":"USER","content":"expensive"}],"maxOutputTokens":32768}
                """);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.body()).contains("AINER.AI.BUDGET_EXCEEDED");
        assertThat(provider.calls()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT policy_decision FROM ainer_ai_invocation",
                String.class)).isEqualTo("REJECTED_BUDGET");
    }

    @Test
    void providerFailureReturnsStableErrorAndMarksAuditFailed() throws Exception {
        provider.failNext();

        HttpResponse<String> response = post("/api/ai/chat/completions", "tenant-failure", """
                {"messages":[{"role":"USER","content":"trigger failure"}],"maxOutputTokens":128}
                """);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("AINER.AI.PROVIDER_UNAVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || error_code FROM ainer_ai_invocation",
                String.class)).isEqualTo("FAILED:AINER.AI.PROVIDER_UNAVAILABLE");
    }

    @Test
    void streamsDeltasThenUsageAndPersistsSuccessfulAudit() throws Exception {
        HttpResponse<String> response = post("/api/ai/chat/completions/stream", "tenant-stream", """
                {"messages":[{"role":"USER","content":"stream it"}],"maxOutputTokens":128}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/event-stream");
        assertThat(response.body())
                .contains("event:delta", "event:usage", "event:done", "Ainer ", "stream");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || streaming::text FROM ainer_ai_invocation",
                String.class)).isEqualTo("SUCCEEDED:true");
    }

    @Test
    void auditLookupIsTenantScoped() throws Exception {
        JsonNode completion = objectMapper.readTree(post("/api/ai/chat/completions", "tenant-owner", """
                {"messages":[{"role":"USER","content":"tenant isolation"}],"maxOutputTokens":128}
                """).body());
        String id = completion.path("data").path("invocationId").stringValue();

        HttpResponse<String> response = get("/api/ai/invocations/" + id, "tenant-other");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("AINER.AI.INVOCATION_NOT_FOUND");
    }

    @Test
    void budgetReservationCountsConcurrentStartedInvocations() {
        CostBreakdown reservation = new CostBreakdown(new BigDecimal("0.00600000"), "USD");
        AiInvocation first = invocation("tenant-reservation", reservation);
        AiInvocation second = invocation("tenant-reservation", reservation);

        assertThat(auditService.reserve(first, new BigDecimal("0.01000000"))).isTrue();
        assertThat(auditService.reserve(second, new BigDecimal("0.01000000"))).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation WHERE status = 'STARTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation WHERE policy_decision = 'REJECTED_BUDGET'",
                Integer.class)).isEqualTo(1);
    }

    private AiInvocation invocation(String tenantId, CostBreakdown reservation) {
        return AiInvocation.started(
                UUID.randomUUID(), tenantId, "subject:test", "request:test", provider.name(),
                "test/model", "test/model", false, "a".repeat(64), reservation, clock.instant());
    }

    private HttpResponse<String> post(String path, String tenantId, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tenantId)
                .header(RequestIds.HEADER, "request-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String tenantId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + tenantId)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AiRuntimeModuleConfiguration.class, FakeProviderConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        @Primary
        TestModelProvider testModelProvider() {
            return new TestModelProvider();
        }

        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("subject:test")
                    .claim("tenant_id", token)
                    .claim("actor_type", "USER")
                    .claim("scope", "ai.invoke")
                    .issuedAt(Instant.now().minusSeconds(5))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }

    static final class TestModelProvider implements ModelProvider {

        private final AtomicBoolean failNext = new AtomicBoolean();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String name() {
            return "test-provider";
        }

        @Override
        public ModelCompletion complete(ModelInvocation invocation) {
            calls.incrementAndGet();
            if (failNext.compareAndSet(true, false)) {
                throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "simulated provider outage");
            }
            return completion();
        }

        @Override
        public void stream(ModelInvocation invocation, ModelStreamObserver observer) {
            calls.incrementAndGet();
            observer.onDelta("Ainer ");
            observer.onDelta("stream");
            observer.onComplete(new ModelCompletion(
                    "provider-stream-1", "test/model", "Ainer stream", "stop",
                    new TokenUsage(8, 2, false)));
        }

        void failNext() {
            failNext.set(true);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            failNext.set(false);
            calls.set(0);
        }

        private ModelCompletion completion() {
            return new ModelCompletion(
                    "provider-request-1", "test/model", "Ainer answer", "stop",
                    new TokenUsage(10, 8, false));
        }
    }
}
