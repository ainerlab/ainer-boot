package dev.ainer.module.ai.gateway.api;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.application.AiGatewayApplicationService;
import dev.ainer.module.ai.gateway.application.AiGatewayErrorCode;
import dev.ainer.module.ai.gateway.application.AiStreamListener;
import dev.ainer.module.ai.gateway.application.ChatCompletionCommand;
import dev.ainer.module.ai.gateway.application.CompletionResult;
import dev.ainer.module.ai.gateway.application.InvocationContext;
import dev.ainer.security.actor.AuthenticatedActor;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Validated
@RestController
@RequestMapping("/api/ai")
public class AiGatewayController {

    private static final String AI_INVOKE_AUTHORITY = "SCOPE_ai.invoke";

    private final AiGatewayApplicationService service;
    private final AiRuntimeProperties properties;
    private final AuthenticatedActorResolver actorResolver;

    public AiGatewayController(
            AiGatewayApplicationService service,
            AiRuntimeProperties properties,
            AuthenticatedActorResolver actorResolver) {
        this.service = service;
        this.properties = properties;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/chat/completions")
    public ApiResponse<ChatCompletionResponse> complete(
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        AuthenticatedActor actor = actorResolver.requireCurrent();
        actor.requireAuthority(AI_INVOKE_AUTHORITY);
        CompletionResult result = service.complete(command(actor, requestId, request));
        return ApiResponse.success(ChatCompletionResponse.from(result), requestId);
    }

    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest servletRequest) {
        String requestId = RequestIds.currentOrCreate(servletRequest);
        AuthenticatedActor actor = actorResolver.requireCurrent();
        actor.requireAuthority(AI_INVOKE_AUTHORITY);
        SseEmitter emitter = new SseEmitter(properties.getProvider().getRequestTimeout().plusSeconds(5).toMillis());
        AtomicReference<Future<?>> task = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        task.set(service.stream(command(actor, requestId, request), new AiStreamListener() {
            @Override
            public void onDelta(UUID invocationId, String delta) {
                send(emitter, "delta", new AiStreamDeltaEvent(invocationId, delta));
            }

            @Override
            public void onComplete(CompletionResult result) {
                send(emitter, "usage", new AiStreamUsageEvent(
                        result.invocationId(), result.completion().providerRequestId(), result.completion().model(),
                        result.completion().finishReason(), TokenUsageResponse.from(result.completion().usage()),
                        CostResponse.from(result.cost()), result.latencyMillis()));
                send(emitter, "done", new AiStreamDeltaEvent(result.invocationId(), ""));
                completed.set(true);
                emitter.complete();
            }

            @Override
            public void onError(UUID invocationId, AiGatewayErrorCode errorCode) {
                send(emitter, "error", new AiStreamErrorEvent(
                        invocationId, errorCode.code(), errorCode.defaultMessage(), requestId));
                completed.set(true);
                emitter.complete();
            }
        }));
        emitter.onTimeout(() -> cancel(task.get()));
        emitter.onError(ignored -> cancel(task.get()));
        emitter.onCompletion(() -> {
            if (!completed.get()) {
                cancel(task.get());
            }
        });
        return emitter;
    }

    @GetMapping("/invocations/{id}")
    public ApiResponse<AiInvocationResponse> invocation(
            @PathVariable UUID id,
        HttpServletRequest servletRequest) {
        AuthenticatedActor actor = actorResolver.requireCurrent();
        actor.requireAuthority(AI_INVOKE_AUTHORITY);
        return ApiResponse.success(
                AiInvocationResponse.from(service.getInvocation(actor.tenantId(), id)),
                RequestIds.currentOrCreate(servletRequest));
    }

    private ChatCompletionCommand command(
            AuthenticatedActor actor,
            String requestId,
            ChatCompletionRequest request) {
        try {
            return new ChatCompletionCommand(
                    new InvocationContext(actor.tenantId(), actor.subjectId(), requestId),
                    request.model(),
                    request.messages().stream().map(ChatMessageRequest::toDomain).toList(),
                    request.effectiveMaxOutputTokens(),
                    request.effectiveTemperature());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AiGatewayErrorCode.INVALID_CONTEXT);
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException exception) {
            throw new StreamDeliveryException(exception);
        }
    }

    private void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static final class StreamDeliveryException extends RuntimeException {
        private StreamDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
