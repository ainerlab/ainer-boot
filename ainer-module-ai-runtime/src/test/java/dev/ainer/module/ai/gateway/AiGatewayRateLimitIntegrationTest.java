package dev.ainer.module.ai.gateway;

import dev.ainer.cache.autoconfigure.AinerCacheCapabilities;
import dev.ainer.cache.ratelimit.RateLimitPort;
import dev.ainer.cache.ratelimit.RedisFixedWindowRateLimitPort;
import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.web.request.RequestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 主体限流经 {@code RateLimitPort} 后的端到端回归（真实 PostgreSQL + 真实 Redis，
 * ADR-0039 §1 第三层能力的真实消费者）。
 *
 * <p>证明两件事：
 * <ol>
 *   <li><strong>对外语义不变</strong>：超过 {@code ainer.ai.limits.requests-per-minute} 的请求仍是
 *       HTTP 429 + {@code AINER.AI.RATE_LIMITED}，审计仍是 {@code REJECTED:REJECTED_RATE_LIMIT}，
 *       且被拒绝的请求不触达 provider；</li>
 *   <li><strong>真的走了 Redis 端口</strong>：{@code ainer.cache.type=redis} 下计数落在
 *       {@code ainer:ratelimit:ai:subject:<sub>:<窗口序号>} 这个共享键上，值为已消耗的配额数——
 *       如果限流仍在进程内自算，Redis 里不会有这个键。</li>
 * </ol>
 *
 * <p>窗口与 epoch 对齐（固定窗口），因此用例开始前会等到一个"新鲜"的分钟窗口，避免在分钟边界上
 * 把「3 次请求落在同一窗口」变成概率事件。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AiGatewayRateLimitIntegrationTest.TestApplication.class,
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
                // 每 subject 每分钟 2 次：第 3 次必须被限流
                "ainer.ai.limits.requests-per-minute=2",
                "ainer.ai.limits.subject-daily-budget=10.00",
                "ainer.ai.pricing.currency=USD",
                "ainer.ai.pricing.input-per-million-tokens=1.00",
                "ainer.ai.pricing.output-per-million-tokens=2.00",
                "ainer.security.resource-server.enabled=true",
                "ainer.cache.type=redis",
                "ainer.cache.rate-limit.key-prefix=ainer:test:ai-ratelimit:",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "spring.main.banner-mode=off"
        })
class AiGatewayRateLimitIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiGatewayRateLimitIntegrationTest.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "ainer:test:ai-ratelimit:";

    private static final long WINDOW_MILLIS = 60_000L;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.3-alpine"))
            .withDatabaseName("ainer_ai_ratelimit_test")
            .withUsername("ainer")
            .withPassword("ainer");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RateLimitPort rateLimitPort;

    @Autowired
    private AinerCacheCapabilities capabilities;

    @Autowired
    private AiGatewayProviderFixture.TestModelProvider provider;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ainer_ai_invocation");
        Set<String> stale = redis.keys(RATE_LIMIT_KEY_PREFIX + "*");
        if (stale != null && !stale.isEmpty()) {
            redis.delete(stale);
        }
        provider.reset();
    }

    @Test
    void gatewayCountsSubjectQuotaInRedisAndKeepsRateLimitContract() throws Exception {
        awaitFreshMinuteWindow();
        String subject = "subject-ratelimit-" + UUID.randomUUID();

        HttpResponse<String> first = post(subject);
        HttpResponse<String> second = post(subject);
        HttpResponse<String> third = post(subject);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        // 错误码不变：仍是被限流
        assertThat(third.statusCode()).isEqualTo(429);
        assertThat(third.body()).contains("AINER.AI.RATE_LIMITED");
        // 被拒绝的请求不触达 provider
        assertThat(provider.calls()).isEqualTo(2);
        // 审计不变：被限流的调用落 REJECTED + REJECTED_RATE_LIMIT（放行的两条各自留下 SUCCEEDED 记录）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || ':' || policy_decision || ':' || error_code FROM ainer_ai_invocation "
                        + "WHERE subject_id = ? AND policy_decision = 'REJECTED_RATE_LIMIT'",
                String.class,
                subject)).isEqualTo("REJECTED:REJECTED_RATE_LIMIT:AINER.AI.RATE_LIMITED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ainer_ai_invocation WHERE subject_id = ?",
                Integer.class,
                subject)).isEqualTo(3);

        // 真实消费者证据：配额计数落在 Redis 的共享键上，值为 2（两个放行请求各消耗 1）
        String storageKey = RATE_LIMIT_KEY_PREFIX + "ai:subject:" + subject + ":" + windowIndex();
        assertThat(redis.opsForValue().get(storageKey))
                .as("AI runtime 必须经 RateLimitPort 在 Redis 里计数（key=%s）", storageKey)
                .isEqualTo("2");
        // 原始实测证据：消费者证据就是这个共享键的值
        LOGGER.info("[ainer-ai-ratelimit] 经 RateLimitPort 的 Redis 计数实测：{}={}（放行 2 / 拒绝 1）",
                storageKey, redis.opsForValue().get(storageKey));
    }

    @Test
    void redisBackedRateLimitIsReportedAsClusterAccurate() {
        assertThat(rateLimitPort).isInstanceOf(RedisFixedWindowRateLimitPort.class);
        assertThat(rateLimitPort.clusterAccurate()).isTrue();
        assertThat(capabilities.rateLimitImplementationClass())
                .isEqualTo(RedisFixedWindowRateLimitPort.class.getName());
        assertThat(capabilities.rateLimitClusterAccurate()).isTrue();
        assertThat(capabilities.describe()).contains("rateLimit{declared=REDIS", "clusterAccurate=true");
    }

    @Test
    void quotaIsPerSubject() throws Exception {
        awaitFreshMinuteWindow();
        String exhausted = "subject-a-" + UUID.randomUUID();
        String other = "subject-b-" + UUID.randomUUID();

        assertThat(post(exhausted).statusCode()).isEqualTo(200);
        assertThat(post(exhausted).statusCode()).isEqualTo(200);
        assertThat(post(exhausted).statusCode()).isEqualTo(429);
        // 另一个 subject 有独立配额
        assertThat(post(other).statusCode()).isEqualTo(200);
    }

    private long windowIndex() {
        return System.currentTimeMillis() / WINDOW_MILLIS;
    }

    /** 等到刚进入一个新的分钟窗口（最少 20 秒余量），让「3 次请求同一窗口」成为确定事实。 */
    private static void awaitFreshMinuteWindow() throws InterruptedException {
        long intoWindow = System.currentTimeMillis() % WINDOW_MILLIS;
        long remaining = WINDOW_MILLIS - intoWindow;
        if (remaining < 20_000L) {
            Thread.sleep(remaining + 300L);
        }
    }

    private HttpResponse<String> post(String subjectId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer "
                        + dev.ainer.testsupport.jwt.JwtTestSupport.signUserJwt(
                                AiGatewayProviderFixture.HTTP_RSA_JWK,
                                "https://auth.ainer.test", "ainer-api", subjectId, "ai.invoke"))
                .header(RequestIds.HEADER, "request-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"model":"test/model","messages":[{"role":"USER","content":"rate limit probe"}],"maxOutputTokens":64}
                        """))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AiRuntimeModuleConfiguration.class, AiGatewayProviderFixture.class})
    static class TestApplication {
    }
}
