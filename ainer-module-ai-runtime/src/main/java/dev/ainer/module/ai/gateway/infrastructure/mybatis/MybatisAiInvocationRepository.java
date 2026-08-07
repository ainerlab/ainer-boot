package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import dev.ainer.module.ai.gateway.application.AiInvocationRepository;
import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.InvocationStatus;
import dev.ainer.module.ai.gateway.domain.PolicyDecision;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisAiInvocationRepository implements AiInvocationRepository {

    private final AiInvocationMapper mapper;

    public MybatisAiInvocationRepository(AiInvocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockSubjectBudget(String subjectId) {
        mapper.lockSubjectBudget(subjectId);
    }

    @Override
    public BigDecimal sumDailyExposure(String subjectId, Instant fromInclusive, Instant toExclusive) {
        return mapper.sumDailyExposure(subjectId, fromInclusive, toExclusive);
    }

    @Override
    public void insert(AiInvocation invocation) {
        if (mapper.insert(toRow(invocation)) != 1) {
            throw new IllegalStateException("AI invocation insert affected an unexpected number of rows");
        }
    }

    @Override
    public boolean markSucceeded(
            UUID id,
            String resolvedModel,
            String providerRequestId,
            TokenUsage usage,
            CostBreakdown actualCost,
            long latencyMillis,
            Instant completedAt) {
        return mapper.markSucceeded(
                id, resolvedModel, providerRequestId, usage.inputTokens(), usage.outputTokens(), usage.estimated(),
                actualCost.amount(), latencyMillis, completedAt) == 1;
    }

    @Override
    public boolean markFailed(UUID id, String errorCode, long latencyMillis, Instant completedAt) {
        return mapper.markFailed(id, errorCode, latencyMillis, completedAt) == 1;
    }

    @Override
    public Optional<AiInvocation> findBySubjectAndId(String subjectId, UUID id) {
        return Optional.ofNullable(mapper.selectBySubjectAndId(subjectId, id)).map(this::toDomain);
    }

    private AiInvocationRow toRow(AiInvocation invocation) {
        AiInvocationRow row = new AiInvocationRow();
        row.setId(invocation.id());
        row.setSubjectId(invocation.subjectId());
        row.setRequestId(invocation.requestId());
        row.setProvider(invocation.provider());
        row.setRequestedModel(invocation.requestedModel());
        row.setResolvedModel(invocation.resolvedModel());
        row.setStreaming(invocation.streaming());
        row.setStatus(invocation.status().name());
        row.setPolicyDecision(invocation.policyDecision().name());
        row.setPromptFingerprint(invocation.promptFingerprint());
        row.setInputTokens(invocation.inputTokens());
        row.setOutputTokens(invocation.outputTokens());
        row.setUsageEstimated(invocation.usageEstimated());
        row.setEstimatedCost(invocation.estimatedCost());
        row.setActualCost(invocation.actualCost());
        row.setCurrency(invocation.currency());
        row.setLatencyMillis(invocation.latencyMillis());
        row.setProviderRequestId(invocation.providerRequestId());
        row.setErrorCode(invocation.errorCode());
        row.setStartedAt(invocation.startedAt());
        row.setCompletedAt(invocation.completedAt());
        return row;
    }

    private AiInvocation toDomain(AiInvocationRow row) {
        return new AiInvocation(
                row.getId(), row.getSubjectId(), row.getRequestId(), row.getProvider(),
                row.getRequestedModel(), row.getResolvedModel(), row.isStreaming(),
                InvocationStatus.valueOf(row.getStatus()), PolicyDecision.valueOf(row.getPolicyDecision()),
                row.getPromptFingerprint(), row.getInputTokens(), row.getOutputTokens(), row.isUsageEstimated(),
                row.getEstimatedCost(), row.getActualCost(), row.getCurrency(), row.getLatencyMillis(),
                row.getProviderRequestId(), row.getErrorCode(), row.getStartedAt(), row.getCompletedAt());
    }
}
