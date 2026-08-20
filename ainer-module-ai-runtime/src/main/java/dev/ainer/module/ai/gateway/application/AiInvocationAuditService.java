package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiInvocation;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.PolicyDecision;
import dev.ainer.module.ai.gateway.domain.TokenUsage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 调用审计服务：负责每次调用的预算预占（悲观锁 + 当日已发生费用核算）、
 * 策略拒绝记录与终态回写（成功/失败）。
 *
 * <p>拒绝与终态回写使用 {@code REQUIRES_NEW} 独立事务提交，保证调用链异常时
 * 审计行仍然落库；审计不保存 prompt 与输出正文，只记录模型、Token/费用、耗时、
 * 结果状态与策略决策。
 */
@Service
public class AiInvocationAuditService {

    private final AiInvocationRepository repository;
    private final Clock clock;

    public AiInvocationAuditService(AiInvocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean reserve(AiInvocation invocation, BigDecimal dailyBudget) {
        repository.lockSubjectBudget(invocation.subjectId());
        Instant from = LocalDate.ofInstant(invocation.startedAt(), ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = from.plusSeconds(86_400);
        BigDecimal exposure = repository.sumDailyExposure(invocation.subjectId(), from, to);
        if (exposure.add(invocation.estimatedCost()).compareTo(dailyBudget) > 0) {
            repository.insert(AiInvocation.rejected(
                    invocation.id(), invocation.subjectId(), invocation.requestId(),
                    invocation.provider(), invocation.requestedModel(), invocation.resolvedModel(),
                    invocation.streaming(), PolicyDecision.REJECTED_BUDGET, invocation.promptFingerprint(),
                    new CostBreakdown(invocation.estimatedCost(), invocation.currency()),
                    AiGatewayErrorCode.BUDGET_EXCEEDED.code(), invocation.startedAt()));
            return false;
        }
        repository.insert(invocation);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(AiInvocation invocation) {
        repository.insert(invocation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(
            UUID id,
            String resolvedModel,
            String providerRequestId,
            TokenUsage usage,
            CostBreakdown cost,
            long latencyMillis) {
        if (!repository.markSucceeded(
                id, resolvedModel, providerRequestId, usage, cost, latencyMillis, clock.instant())) {
            throw new IllegalStateException("AI invocation success update affected no STARTED row");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, String errorCode, long latencyMillis) {
        if (!repository.markFailed(id, errorCode, latencyMillis, clock.instant())) {
            throw new IllegalStateException("AI invocation failure update affected no STARTED row");
        }
    }

    @Transactional(readOnly = true)
    public AiInvocation get(String subjectId, UUID id) {
        Optional<AiInvocation> invocation = repository.findBySubjectAndId(subjectId, id);
        return invocation.orElseThrow(() -> new dev.ainer.core.error.BusinessException(
                AiGatewayErrorCode.INVOCATION_NOT_FOUND));
    }
}
