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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleModelProviderTest {

    private final AtomicReference<HttpHandler> handler = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private OpenAiCompatibleModelProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handler.get().handle(exchange));
        server.start();

        AiRuntimeProperties.Provider properties = new AiRuntimeProperties.Provider(
                "contract-provider",
                "http://localhost:" + server.getAddress().getPort(),
                "contract-secret",
                "test/model",
                List.of("test/model"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                true);
        provider = new OpenAiCompatibleModelProvider(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                objectMapper,
                new TokenEstimator());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
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

        provider.stream(invocation(), new ModelStreamObserver() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(ModelCompletion result) {
                completion.set(result);
            }
        });

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

        assertThatThrownBy(() -> provider.stream(invocation(), new ModelStreamObserver() {
            @Override
            public void onDelta(String delta) {
            }

            @Override
            public void onComplete(ModelCompletion completion) {
            }
        })).isInstanceOfSatisfying(ProviderFailure.class, failure ->
                assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.PROTOCOL));
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
