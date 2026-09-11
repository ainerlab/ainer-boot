package dev.ainer.module.ai.gateway.infrastructure.openai;

import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.application.ModelProvider;
import dev.ainer.module.ai.gateway.application.ModelStreamObserver;
import dev.ainer.module.ai.gateway.application.ProviderFailure;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import dev.ainer.module.ai.gateway.policy.TokenEstimator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI 兼容 HTTP 模型提供方：以 chat/completions 协议对接任意兼容网关
 * （OpenAI、DeepSeek 等）。
 *
 * <p>支持非流式与 SSE 流式两条路径；对响应体大小与流内容长度设置硬上限，
 * 供应商错误分类为 {@link ProviderFailure}（限流/超时/不可用/协议错误），
 * 错误正文不透出；usage 缺失时用 {@link TokenEstimator} 估算。
 *
 * <p><b>总时长上限（覆盖响应体读取）</b>：{@code HttpRequest.timeout} 按 JDK 定性
 * （JDK-8258397）只在「响应头到达」前有效，一旦上游发完响应头就静默，后续
 * {@code readLine()} / {@code readNBytes()} 会无限阻塞。因此整次调用（发送 + 读完响应体）
 * 都提交到独立执行器，由调用线程用 {@code Future.get(totalTimeout)} 兜底：超时即
 * cancel + 关闭响应体（唤醒阻塞中的读取线程），并把调用推进到
 * {@link ProviderFailure.Kind#TIMEOUT} 终态，绝不永卡中间态。
 */
public final class OpenAiCompatibleModelProvider implements ModelProvider {

    private static final int MAX_NON_STREAM_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_STREAM_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STREAM_CONTENT_CHARACTERS = 2 * 1024 * 1024;

    private final String name;
    private final String apiKey;
    private final URI completionsUri;
    private final Duration requestTimeout;
    private final Duration totalTimeout;
    private final Duration streamTotalTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenEstimator tokenEstimator;
    private final ExecutorService callExecutor;

    public OpenAiCompatibleModelProvider(
            AiRuntimeProperties.Provider properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            TokenEstimator tokenEstimator,
            ExecutorService callExecutor) {
        this.name = properties.getName();
        this.apiKey = properties.getApiKey();
        this.completionsUri = completionsUri(properties.getBaseUrl());
        this.requestTimeout = properties.getRequestTimeout();
        this.totalTimeout = properties.getTotalTimeout();
        this.streamTotalTimeout = properties.getStreamTotalTimeout();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenEstimator = tokenEstimator;
        this.callExecutor = callExecutor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ModelCompletion complete(ModelInvocation invocation) {
        return callWithinDeadline("completion", totalTimeout, (responseBody, deadlineNanos) -> {
            HttpResponse<InputStream> response = send(invocation, false);
            responseBody.register(response.body());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    drain(body);
                    throw statusFailure(response.statusCode());
                }
                String json = readLimited(body);
                return parseCompletion(json, invocation);
            } catch (ProviderFailure failure) {
                throw failure;
            } catch (IOException exception) {
                throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "Failed to read provider response", exception);
            }
        });
    }

    @Override
    public void stream(ModelInvocation invocation, ModelStreamObserver observer) {
        Objects.requireNonNull(observer, "observer");
        callWithinDeadline("stream", streamTotalTimeout, (responseBody, deadlineNanos) -> {
            HttpResponse<InputStream> response = send(invocation, true);
            responseBody.register(response.body());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    drain(body);
                    throw statusFailure(response.statusCode());
                }
                readStream(body, invocation, observer, deadlineNanos);
                return null;
            } catch (ProviderFailure failure) {
                throw failure;
            } catch (IOException exception) {
                throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "Failed to read provider stream", exception);
            }
        });
    }

    /**
     * 在有界总时长内执行一次 provider 调用：调用线程只等待 {@code timeout}，超时后取消任务并
     * 关闭响应体，使阻塞在 {@code read()} 的读取线程立即以 IOException 结束（不留下永久阻塞
     * 的线程），随后抛出 {@link ProviderFailure.Kind#TIMEOUT}。
     *
     * <p>不使用 try-with-resources 包装执行器：{@code close()} 会无限等待未结束的任务。
     * 执行器由装配层管理生命周期。
     */
    private <T> T callWithinDeadline(String operation, Duration timeout, BoundedCall<T> call) {
        long timeoutMillis = Math.max(1L, timeout.toMillis());
        ResponseBodyHandle responseBody = new ResponseBodyHandle();
        Future<T> future;
        try {
            future = callExecutor.submit(() -> call.run(responseBody, System.nanoTime() + timeout.toNanos()));
        } catch (RejectedExecutionException exception) {
            throw new ProviderFailure(
                    ProviderFailure.Kind.UNAVAILABLE, "Provider call executor rejected the request", exception);
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            responseBody.abort();
            throw new ProviderFailure(ProviderFailure.Kind.TIMEOUT,
                    "Provider " + operation + " exceeded the total timeout of " + timeout.toSeconds()
                            + "s including the response body read");
        } catch (InterruptedException exception) {
            // 客户端断开或停机中断：仍要把调用推进到终态（由调用方写审计），所以不把中断标记
            // 留在当前线程上，避免随后的审计回写因中断标记失败而让记录停在中间态。
            future.cancel(true);
            responseBody.abort();
            throw new ProviderFailure(
                    ProviderFailure.Kind.UNAVAILABLE, "Provider " + operation + " was interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ProviderFailure failure) {
                throw failure;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ProviderFailure(
                    ProviderFailure.Kind.UNAVAILABLE, "Provider " + operation + " failed", cause);
        }
    }

    /**
     * 在途响应体句柄：调用线程超时/中断时置 aborted 并关闭已注册的响应体；读取线程注册响应体时，
     * 若发现调用已被放弃就立刻关掉自己手里的流。读写都在同一把锁下，因此不存在
     * 「超时恰好发生在 send() 返回与注册之间」导致读取线程永久阻塞在 read() 上的窗口。
     */
    private static final class ResponseBodyHandle {

        private InputStream body;
        private boolean aborted;

        synchronized void register(InputStream responseBody) throws IOException {
            if (aborted) {
                responseBody.close();
                throw new ProviderFailure(ProviderFailure.Kind.TIMEOUT,
                        "Provider call was already aborted before the response body was registered");
            }
            this.body = responseBody;
        }

        synchronized void abort() {
            aborted = true;
            if (body == null) {
                return;
            }
            try {
                body.close();
            } catch (IOException ignored) {
                // 关闭失败只影响唤醒速度：调用线程已按超时返回，读取线程另有中断兜底。
            }
        }
    }

    @FunctionalInterface
    private interface BoundedCall<T> {
        T run(ResponseBodyHandle responseBody, long deadlineNanos) throws IOException;
    }

    private HttpResponse<InputStream> send(ModelInvocation invocation, boolean stream) {
        HttpRequest request = HttpRequest.newBuilder(completionsUri)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(invocation, stream), StandardCharsets.UTF_8))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.TIMEOUT, "Provider request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "Provider request was interrupted", exception);
        } catch (IOException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "Provider connection failed", exception);
        }
    }

    private String requestBody(ModelInvocation invocation, boolean stream) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", invocation.model());
        List<Map<String, String>> messages = new ArrayList<>();
        invocation.messages().forEach(message -> messages.add(Map.of(
                "role", message.role().name().toLowerCase(Locale.ROOT),
                "content", message.content())));
        request.put("messages", messages);
        request.put("max_tokens", invocation.maxOutputTokens());
        request.put("temperature", invocation.temperature());
        request.put("stream", stream);
        if (stream) {
            request.put("stream_options", Map.of("include_usage", true));
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.PROTOCOL, "Failed to encode provider request", exception);
        }
    }

    private ModelCompletion parseCompletion(String json, ModelInvocation invocation) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw protocol("Provider response contains no choices");
            }
            JsonNode first = choices.get(0);
            JsonNode contentNode = first.path("message").path("content");
            if (!contentNode.isString()) {
                throw protocol("Provider response contains no text content");
            }
            String content = contentNode.stringValue();
            TokenUsage usage = usage(root.path("usage"), invocation, content);
            return new ModelCompletion(
                    requiredText(root, "id"),
                    requiredText(root, "model"),
                    content,
                    first.path("finish_reason").stringValue("unknown"),
                    usage);
        } catch (ProviderFailure failure) {
            throw failure;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.PROTOCOL, "Provider returned invalid JSON", exception);
        }
    }

    private void readStream(InputStream input, ModelInvocation invocation, ModelStreamObserver observer,
                            long deadlineNanos) throws IOException {
        StreamState state = new StreamState();
        boolean done = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new LimitedInputStream(input, MAX_STREAM_RESPONSE_BYTES), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 流式路径的进度以「读完一行」为单位：上游持续慢速吐字节时，让读取循环自己在
                // 截止时间点失败，而不是等到调用线程的 Future.get 超时。
                if (System.nanoTime() - deadlineNanos > 0) {
                    throw new ProviderFailure(ProviderFailure.Kind.TIMEOUT,
                            "Provider stream exceeded the total timeout including the response body read");
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    done = true;
                    break;
                }
                consumeChunk(data, state, observer);
            }
        }
        if (!done) {
            throw protocol("Provider stream ended before the DONE marker");
        }
        if (state.providerRequestId == null || state.model == null) {
            throw protocol("Provider stream omitted required metadata");
        }
        TokenUsage usage = state.usage == null
                ? tokenEstimator.estimateUsage(invocation.messages(), state.content.toString())
                : state.usage;
        try {
            observer.onComplete(new ModelCompletion(
                    state.providerRequestId,
                    state.model,
                    state.content.toString(),
                    state.finishReason,
                    usage));
        } catch (IllegalArgumentException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.PROTOCOL, "Provider stream metadata is invalid", exception);
        }
    }

    private void consumeChunk(String data, StreamState state, ModelStreamObserver observer) {
        try {
            JsonNode root = objectMapper.readTree(data);
            String requestId = optionalText(root, "id");
            if (requestId != null) {
                state.providerRequestId = requestId;
            }
            String model = optionalText(root, "model");
            if (model != null) {
                state.model = model;
            }
            JsonNode usageNode = root.path("usage");
            if (usageNode.isObject() && usageNode.has("prompt_tokens") && usageNode.has("completion_tokens")) {
                state.usage = new TokenUsage(
                        usageNode.path("prompt_tokens").asInt(),
                        usageNode.path("completion_tokens").asInt(),
                        false);
            }
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                String delta = first.path("delta").path("content").stringValue("");
                if (!delta.isEmpty()) {
                    if ((long) state.content.length() + delta.length() > MAX_STREAM_CONTENT_CHARACTERS) {
                        throw protocol("Provider stream exceeded the maximum content size");
                    }
                    state.content.append(delta);
                    observer.onDelta(delta);
                }
                String finishReason = first.path("finish_reason").stringValue("");
                if (!finishReason.isEmpty()) {
                    state.finishReason = finishReason;
                }
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ProviderFailure(ProviderFailure.Kind.PROTOCOL, "Provider returned an invalid stream chunk", exception);
        }
    }

    private TokenUsage usage(JsonNode usage, ModelInvocation invocation, String content) {
        if (usage.isObject() && usage.has("prompt_tokens") && usage.has("completion_tokens")) {
            return new TokenUsage(
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    false);
        }
        return tokenEstimator.estimateUsage(invocation.messages(), content);
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw protocol("Provider response omitted " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private String readLimited(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_NON_STREAM_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_NON_STREAM_RESPONSE_BYTES) {
            throw protocol("Provider response exceeded the maximum size");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void drain(InputStream input) throws IOException {
        input.readNBytes(64 * 1024);
    }

    private ProviderFailure statusFailure(int status) {
        if (status == 429) {
            return new ProviderFailure(ProviderFailure.Kind.RATE_LIMITED, "Provider rate limited the request");
        }
        if (status == 408 || status == 504) {
            return new ProviderFailure(ProviderFailure.Kind.TIMEOUT, "Provider reported a timeout");
        }
        return new ProviderFailure(ProviderFailure.Kind.UNAVAILABLE, "Provider returned HTTP " + status);
    }

    private ProviderFailure protocol(String message) {
        return new ProviderFailure(ProviderFailure.Kind.PROTOCOL, message);
    }

    private URI completionsUri(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized.endsWith("/v1")
                ? normalized + "/chat/completions"
                : normalized + "/v1/chat/completions");
    }

    private static final class StreamState {
        private String providerRequestId;
        private String model;
        private String finishReason = "unknown";
        private final StringBuilder content = new StringBuilder();
        private TokenUsage usage;
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long consumed;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increment(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                increment(read);
            }
            return read;
        }

        private void increment(int count) {
            consumed += count;
            if (consumed > limit) {
                throw new ProviderFailure(
                        ProviderFailure.Kind.PROTOCOL,
                        "Provider stream exceeded the maximum response size");
            }
        }
    }
}
