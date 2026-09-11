package dev.ainer.module.ai.gateway.infrastructure.openai;

import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.application.ModelStreamObserver;
import dev.ainer.module.ai.gateway.application.ProviderFailure;
import dev.ainer.module.ai.gateway.domain.MessageRole;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;
import dev.ainer.module.ai.gateway.domain.ModelMessage;
import dev.ainer.module.ai.gateway.policy.TokenEstimator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenAI 兼容 provider 的合约测试。
 *
 * <p>重点是「上游发完响应头就静默」：JDK 的 {@code HttpRequest.timeout} 按 JDK-8258397 只覆盖到
 * 响应头，因此这类桩必须让调用在有界时间内以 TIMEOUT 失败，而不是把线程永久挂在
 * {@code readLine()} / {@code readNBytes()} 上。
 */
class OpenAiCompatibleModelProviderTest {

    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService callExecutor;
    private OpenAiCompatibleModelProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handler.get().handle(exchange));
        // 处理「发完响应头后静默」的桩会长时间阻塞，用虚拟线程跑 handler：
        // 既不会占满平台线程，也能在 teardown 时被 shutdownNow() 立即打断。
        serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.start();

        callExecutor = Executors.newVirtualThreadPerTaskExecutor();
        provider = provider(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverExecutor.shutdownNow();
        callExecutor.shutdownNow();
    }

    @Test
    void sendsCompatibleRequestAndParsesCompletionUsage() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        handler.set(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer contract-secret");
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "application/json", """
                    {"id":"chatcmpl-1","model":"test/model","choices":[{"message":{"content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                    """);
        });

        ModelCompletion completion = provider.complete(invocation());

        assertThat(completion.content()).isEqualTo("hello");
        assertThat(completion.usage().inputTokens()).isEqualTo(11);
        assertThat(completion.usage().outputTokens()).isEqualTo(7);
        JsonNode sent = objectMapper.readTree(requestBody.get());
        assertThat(sent.path("stream").booleanValue()).isFalse();
        assertThat(sent.path("messages").get(0).path("role").stringValue()).isEqualTo("user");
    }

    @Test
    void parsesSseDeltasAndFinalUsageChunk() {
        handler.set(exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(request).contains("\"stream\":true", "\"include_usage\":true");
            respond(exchange, 200, "text/event-stream", """
                    data: {"id":"chatcmpl-stream","model":"test/model","choices":[{"delta":{"content":"Ainer "},"finish_reason":null}]}

                    data: {"id":"chatcmpl-stream","model":"test/model","choices":[{"delta":{"content":"AI"},"finish_reason":"stop"}]}

                    data: {"id":"chatcmpl-stream","model":"test/model","choices":[],"usage":{"prompt_tokens":9,"completion_tokens":2,"total_tokens":11}}

                    data: [DONE]

                    """);
        });
        List<String> deltas = new ArrayList<>();
        AtomicReference<ModelCompletion> completion = new AtomicReference<>();

        provider.stream(invocation(), observer(deltas, completion));

        assertThat(deltas).containsExactly("Ainer ", "AI");
        assertThat(completion.get().content()).isEqualTo("Ainer AI");
        assertThat(completion.get().usage().totalTokens()).isEqualTo(11);
        assertThat(completion.get().usage().estimated()).isFalse();
    }

    @Test
    void estimatesUsageWhenCompatibleProviderOmitsUsage() {
        handler.set(exchange -> respond(exchange, 200, "application/json", """
                {"id":"chatcmpl-estimated","model":"test/model","choices":[{"message":{"content":"estimated response"},"finish_reason":"stop"}]}
                """));

        ModelCompletion completion = provider.complete(invocation());

        assertThat(completion.usage().estimated()).isTrue();
        assertThat(completion.usage().totalTokens()).isPositive();
    }

    @Test
    void mapsProviderRateLimitWithoutExposingResponseBody() {
        handler.set(exchange -> respond(exchange, 429, "application/json", "{\"error\":\"provider secret\"}"));

        assertThatThrownBy(() -> provider.complete(invocation()))
                .isInstanceOfSatisfying(ProviderFailure.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.RATE_LIMITED);
                    assertThat(failure.getMessage()).doesNotContain("provider secret");
                });
    }

    @Test
    void rejectsAnUnboundedSseResponse() {
        handler.set(exchange -> respond(exchange, 200, "text/event-stream", """
                data: {"id":"chatcmpl-stream","model":"test/model","choices":[],"padding":"%s"}

                data: [DONE]

                """.formatted("x".repeat(4 * 1024 * 1024))));

        assertThatThrownBy(() -> provider.stream(invocation(), observer(new ArrayList<>(), new AtomicReference<>())))
                .isInstanceOfSatisfying(ProviderFailure.class, failure ->
                        assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.PROTOCOL));
    }

    /**
     * 交付物 1 的核心断言：上游「发完响应头 + 一小段正文后静默」时，非流式调用必须在有界时间内以
     * TIMEOUT 失败。
     *
     * <p>桩必须先写出一段正文：JDK 的 {@code com.sun.net.httpserver.HttpServer} 在 chunked 响应下
     * 要等到第一次写正文才把响应头刷到 socket 上（实测 headers-only 时客户端 60s 都拿不到响应头）。
     * 这也正是缺陷的真实形态——一旦客户端拿到响应头，{@code HttpRequest.timeout} 就不再生效
     * （JDK-8258397），后续 {@code readNBytes()} 会无限阻塞。
     */
    @Test
    void nonStreamingCallFailsWithinBoundedTimeWhenProviderSendsHeadersThenGoesSilent() {
        // 响应头超时 300ms、总超时 1200ms：响应头早已到达，因此失败只能来自「覆盖 body 读取的总超时」
        OpenAiCompatibleModelProvider bounded = provider(
                Duration.ofMillis(300), Duration.ofMillis(1_200), Duration.ofSeconds(2));
        handler.set(OpenAiCompatibleModelProviderTest::respondPartialBodyThenGoSilent);

        long startedNanos = System.nanoTime();
        assertThatThrownBy(() -> bounded.complete(invocation()))
                .isInstanceOfSatisfying(ProviderFailure.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.TIMEOUT);
                    assertThat(failure.getMessage()).contains("total timeout", "response body read");
                });
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        // 区间断言同时证明两件事：有界（不会挂死）且不是请求头超时兜的底
        assertThat(elapsedMillis).isBetween(1_000L, 5_000L);
    }

    /** 流式路径同样必须有界：SSE 响应头已到、正文卡在半途时不得挂死。 */
    @Test
    void streamingCallFailsWithinBoundedTimeWhenProviderSendsHeadersThenGoesSilent() {
        // 响应头超时 300ms、流式总超时 1200ms（第一个 SSE 分片已让响应头到达）
        OpenAiCompatibleModelProvider bounded = provider(
                Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(1_200));
        handler.set(OpenAiCompatibleModelProviderTest::respondOneChunkThenGoSilent);

        long startedNanos = System.nanoTime();
        assertThatThrownBy(() -> bounded.stream(invocation(), observer(new ArrayList<>(), new AtomicReference<>())))
                .isInstanceOfSatisfying(ProviderFailure.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.TIMEOUT);
                    assertThat(failure.getMessage()).contains("total timeout", "response body read");
                });
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(elapsedMillis).isBetween(1_000L, 5_000L);
    }

    /**
     * 超时之后同一个 provider（同一个 HttpClient）必须还能继续正常工作：
     * 看门狗关闭响应体只是取消在途交换，不会污染客户端连接池。
     */
    @Test
    void recoversAfterBoundedTimeoutOnTheSameHttpClient() {
        OpenAiCompatibleModelProvider bounded =
                provider(Duration.ofMillis(400), Duration.ofMillis(400));
        handler.set(OpenAiCompatibleModelProviderTest::respondOneChunkThenGoSilent);
        assertThatThrownBy(() -> bounded.stream(invocation(), observer(new ArrayList<>(), new AtomicReference<>())))
                .isInstanceOfSatisfying(ProviderFailure.class, failure ->
                        assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.TIMEOUT));

        handler.set(exchange -> respond(exchange, 200, "application/json", """
                {"id":"chatcmpl-after-timeout","model":"test/model","choices":[{"message":{"content":"recovered"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """));

        assertThat(bounded.complete(invocation()).content()).isEqualTo("recovered");
    }

    /**
     * 慢速滴流（每 150ms 一个合法分片、永不发 DONE）也必须被总预算截断：
     * 读取循环自己在截止时间点失败，而不是依赖调用线程的 {@code Future.get}。
     */
    @Test
    void streamingCallFailsWithinBoundedTimeWhenProviderDripsChunksForever() {
        OpenAiCompatibleModelProvider bounded =
                provider(Duration.ofSeconds(2), Duration.ofMillis(600));
        handler.set(OpenAiCompatibleModelProviderTest::dripChunksForever);

        long startedNanos = System.nanoTime();
        assertThatThrownBy(() -> bounded.stream(invocation(), observer(new ArrayList<>(), new AtomicReference<>())))
                .isInstanceOfSatisfying(ProviderFailure.class, failure ->
                        assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.TIMEOUT));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        assertThat(elapsedMillis).isLessThan(5_000L);
    }

    private OpenAiCompatibleModelProvider provider(Duration totalTimeout, Duration streamTotalTimeout) {
        return provider(Duration.ofSeconds(2), totalTimeout, streamTotalTimeout);
    }

    /**
     * @param requestTimeout     {@code HttpRequest.timeout}：只覆盖到响应头（JDK-8258397）
     * @param totalTimeout       覆盖响应体读取的整次非流式调用上限
     * @param streamTotalTimeout 覆盖响应体读取的整次流式调用上限
     */
    private OpenAiCompatibleModelProvider provider(
            Duration requestTimeout, Duration totalTimeout, Duration streamTotalTimeout) {
        AiRuntimeProperties.Provider properties = new AiRuntimeProperties.Provider(
                "contract-provider",
                "http://localhost:" + server.getAddress().getPort(),
                "contract-secret",
                "test/model",
                List.of("test/model"),
                Duration.ofSeconds(1),
                requestTimeout,
                totalTimeout,
                streamTotalTimeout,
                true);
        return new OpenAiCompatibleModelProvider(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                objectMapper,
                new TokenEstimator(),
                callExecutor);
    }

    private ModelStreamObserver observer(List<String> deltas, AtomicReference<ModelCompletion> completion) {
        return new ModelStreamObserver() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelCompletion result) {
                completion.set(result);
            }
        };
    }

    /**
     * 非流式「发完响应头就静默」：声明 Content-Length=128 但只写出 8 字节再 flush，
     * 客户端随后在 {@code readNBytes()} 上无限阻塞。
     */
    private static void respondPartialBodyThenGoSilent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 128);
        exchange.getResponseBody().write("{\"id\":\"x".getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        pause(30_000L);
        exchange.close();
    }

    /**
     * 流式「发完响应头就静默」：chunked 响应先吐一个合法 SSE 分片（响应头随之刷出），
     * 之后永不发第二个分片、也永不发 [DONE]。
     */
    private static void respondOneChunkThenGoSilent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(("data: {\"id\":\"chatcmpl-silent\",\"model\":\"test/model\","
                + "\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}\n\n")
                .getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        pause(30_000L);
        exchange.close();
    }

    /** 持续滴流合法分片、永不发 [DONE]：模拟上游慢速但不断流。 */
    private static void dripChunksForever(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        String chunk = "data: {\"id\":\"chatcmpl-drip\",\"model\":\"test/model\","
                + "\"choices\":[{\"delta\":{\"content\":\"x\"},\"finish_reason\":null}]}\n\n";
        for (int index = 0; index < 200; index++) {
            if (!pause(150L)) {
                break;
            }
            try {
                exchange.getResponseBody().write(chunk.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            } catch (IOException clientGone) {
                break;
            }
        }
        exchange.close();
    }

    /** 返回 false 表示线程被要求停止（测试收尾），调用方应尽快结束 handler。 */
    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ModelInvocation invocation() {
        return new ModelInvocation(
                UUID.randomUUID(),
                "test/model",
                List.of(new ModelMessage(MessageRole.USER, "hello")),
                128,
                new java.math.BigDecimal("0.7"));
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
