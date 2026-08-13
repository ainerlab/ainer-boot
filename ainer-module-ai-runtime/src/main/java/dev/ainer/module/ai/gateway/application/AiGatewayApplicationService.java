package dev.ainer.module.ai.gateway.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;
import dev.ainer.module.ai.gateway.domain.PolicyDecision;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import dev.ainer.module.ai.gateway.policy.CostCalculator;
import dev.ainer.module.ai.gateway.policy.PromptFingerprint;
import dev.ainer.module.ai.gateway.policy.SensitiveDataPolicy;
import dev.ainer.module.ai.gateway.policy.SubjectRateLimiter;
import dev.ainer.module.ai.gateway.policy.TokenEstimator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiGatewayApplicationService {

    private final AiRuntimeProperties properties;
    private final ModelProvider provider;
    private final AiInvocationAuditService auditService;
    private final TokenEstimator tokenEstimator;
    private final CostCalculator costCalculator;
    private final PromptFingerprint promptFingerprint;
    private final SensitiveDataPolicy sensitiveDataPolicy;
    private final SubjectRateLimiter rateLimiter;
    private final ExecutorService streamExecutor;
    private final Clock clock;

    public AiGatewayApplicationService(
            AiRuntimeProperties properties,
            ModelProvider provider,
            AiInvocationAuditService auditService,
            TokenEstimator tokenEstimator,
            CostCalculator costCalculator,
            PromptFingerprint promptFingerprint,
            SensitiveDataPolicy sensitiveDataPolicy,
            SubjectRateLimiter rateLimiter,
            @Qualifier("aiStreamExecutor") ExecutorService streamExecutor,
            Clock clock) {
        this.properties = properties;
        this.provider = provider;
        this.auditService = auditService;
        this.tokenEstimator = tokenEstimator;
        this.costCalculator = costCalculator;
        this.promptFingerprint = promptFingerprint;
        this.sensitiveDataPolicy = sensitiveDataPolicy;
        this.rateLimiter = rateLimiter;
        this.streamExecutor = streamExecutor;
        this.clock = clock;
    }

    public CompletionResult complete(ChatCompletionCommand command) {
        PreparedInvocation prepared = prepare(command, false);
        long startedNanos = System.nanoTime();
        try {
            ModelCompletion completion = provider.complete(prepared.modelInvocation());
            long latency = elapsedMillis(startedNanos);
            CostBreakdown cost = costCalculator.calculate(completion.usage());
            auditService.succeed(
                    prepared.id(), completion.model(), completion.providerRequestId(), completion.usage(), cost, latency);
            return new CompletionResult(prepared.id(), completion, cost, latency);
        } catch (ProviderFailure failure) {
            AiGatewayErrorCode errorCode = providerError(failure);
            auditService.fail(prepared.id(), errorCode.code(), elapsedMillis(startedNanos));
            throw new BusinessException(errorCode);
        } catch (RuntimeException failure) {
            auditService.fail(prepared.id(), "AINER.COMMON.INTERNAL_ERROR", elapsedMillis(startedNanos));
            throw failure;
        }
    }

    public Future<?> stream(ChatCompletionCommand command, AiStreamListener listener) {
        PreparedInvocation prepared = prepare(command, true);
        return streamExecutor.submit(() -> executeStream(prepared, listener));
    }

    public AiInvocation getInvocation(String subjectId, UUID id) {
        return auditService.get(subjectId, id);
    }

    private void executeStream(PreparedInvocation prepared, AiStreamListener listener) {
        long startedNanos = System.nanoTime();
        AtomicBoolean auditCompleted = new AtomicBoolean();
        try {
            provider.stream(prepared.modelInvocation(), new ModelStreamObserver() {
                @Override
                public void onDelta(String delta) {
                    listener.onDelta(prepared.id(), delta);
                }

                @Override
                public void onComplete(ModelCompletion completion) {
                    long latency = elapsedMillis(startedNanos);
                    CostBreakdown cost = costCalculator.calculate(completion.usage());
                    auditService.succeed(
                            prepared.id(), completion.model(), completion.providerRequestId(),
                            completion.usage(), cost, latency);
                    auditCompleted.set(true);
                    listener.onComplete(new CompletionResult(prepared.id(), completion, cost, latency));
                }
            });
        } catch (ProviderFailure failure) {
            if (auditCompleted.get()) {
                return;
            }
            AiGatewayErrorCode errorCode = providerError(failure);
            auditService.fail(prepared.id(), errorCode.code(), elapsedMillis(startedNanos));
            notifyError(listener, prepared.id(), errorCode);
        } catch (RuntimeException failure) {
            if (auditCompleted.get()) {
                return;
            }
            auditService.fail(prepared.id(), "AINER.COMMON.INTERNAL_ERROR", elapsedMillis(startedNanos));
            notifyError(listener, prepared.id(), AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private void notifyError(AiStreamListener listener, UUID id, AiGatewayErrorCode errorCode) {
        try {
            listener.onError(id, errorCode);
        } catch (RuntimeException ignored) {
            // The client may already have disconnected; the audit row is authoritative.
        }
    }

    private PreparedInvocation prepare(ChatCompletionCommand command, boolean streaming) {
        if (command == null || command.messages().isEmpty() || command.messages().size() > 100
                || command.maxOutputTokens() < 1 || command.maxOutputTokens() > 32_768
                || command.temperature().signum() < 0
                || command.temperature().compareTo(new BigDecimal("2")) > 0) {
            throw new BusinessException(AiGatewayErrorCode.INVALID_REQUEST);
        }

        UUID id = UUID.randomUUID();
        Instant startedAt = clock.instant();
        String requestedModel = command.requestedModel().isBlank()
                ? properties.getProvider().getDefaultModel()
                : command.requestedModel();
        String resolvedModel = requestedModel;
        String fingerprint = promptFingerprint.digest(command.messages());
        TokenUsage estimatedUsage = new TokenUsage(
                tokenEstimator.estimateInputTokens(command.messages()), command.maxOutputTokens(), true);
        CostBreakdown estimatedCost = costCalculator.calculate(estimatedUsage);

        if (!properties.getProvider().effectiveAllowedModels().contains(resolvedModel)) {
            reject(id, command, requestedModel, resolvedModel, streaming, fingerprint, estimatedCost,
                    PolicyDecision.REJECTED_MODEL, AiGatewayErrorCode.MODEL_NOT_ALLOWED, startedAt);
        }

        long promptCharacters = command.messages().stream()
                .mapToLong(message -> message.content().codePointCount(0, message.content().length()))
                .sum();
        if (promptCharacters > properties.getLimits().getMaxPromptCharacters()) {
            reject(id, command, requestedModel, resolvedModel, streaming, fingerprint, estimatedCost,
                    PolicyDecision.REJECTED_PROMPT_SIZE, AiGatewayErrorCode.PROMPT_TOO_LARGE, startedAt);
        }
        if (sensitiveDataPolicy.containsDeniedData(command.messages())) {
            reject(id, command, requestedModel, resolvedModel, streaming, fingerprint, estimatedCost,
                    PolicyDecision.REJECTED_SENSITIVE_DATA, AiGatewayErrorCode.SENSITIVE_DATA_REJECTED, startedAt);
        }
        if (!rateLimiter.tryAcquire(command.context().subjectId())) {
            reject(id, command, requestedModel, resolvedModel, streaming, fingerprint, estimatedCost,
                    PolicyDecision.REJECTED_RATE_LIMIT, AiGatewayErrorCode.RATE_LIMITED, startedAt);
        }

        AiInvocation audit = AiInvocation.started(
                id, command.context().subjectId(), command.context().requestId(),
                provider.name(), requestedModel, resolvedModel, streaming, fingerprint, estimatedCost, startedAt);
        if (!auditService.reserve(audit, properties.getLimits().getSubjectDailyBudget())) {
            throw new BusinessException(AiGatewayErrorCode.BUDGET_EXCEEDED);
        }
        return new PreparedInvocation(
                id,
                new ModelInvocation(id, resolvedModel, command.messages(),
                        command.maxOutputTokens(), command.temperature()));
    }

    private void reject(
            UUID id,
            ChatCompletionCommand command,
            String requestedModel,
            String resolvedModel,
            boolean streaming,
            String fingerprint,
            CostBreakdown estimatedCost,
            PolicyDecision decision,
            AiGatewayErrorCode errorCode,
            Instant now) {
        auditService.reject(AiInvocation.rejected(
                id, command.context().subjectId(), command.context().requestId(),
                provider.name(), requestedModel, resolvedModel, streaming, decision, fingerprint,
                estimatedCost, errorCode.code(), now));
        throw new BusinessException(errorCode);
    }

    private AiGatewayErrorCode providerError(ProviderFailure failure) {
        return switch (failure.kind()) {
            case RATE_LIMITED -> AiGatewayErrorCode.PROVIDER_RATE_LIMITED;
            case TIMEOUT -> AiGatewayErrorCode.PROVIDER_TIMEOUT;
            case UNAVAILABLE -> AiGatewayErrorCode.PROVIDER_UNAVAILABLE;
            case PROTOCOL -> AiGatewayErrorCode.PROVIDER_PROTOCOL_ERROR;
        };
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private record PreparedInvocation(UUID id, ModelInvocation modelInvocation) {
    }
}
