package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface AiInvocationMapper {

    String lockTenantBudget(@Param("tenantId") String tenantId);

    BigDecimal sumDailyExposure(
            @Param("tenantId") String tenantId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    int insert(AiInvocationRow row);

    int markSucceeded(
            @Param("id") UUID id,
            @Param("resolvedModel") String resolvedModel,
            @Param("providerRequestId") String providerRequestId,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("usageEstimated") boolean usageEstimated,
            @Param("actualCost") BigDecimal actualCost,
            @Param("latencyMillis") long latencyMillis,
            @Param("completedAt") Instant completedAt);

    int markFailed(
            @Param("id") UUID id,
            @Param("errorCode") String errorCode,
            @Param("latencyMillis") long latencyMillis,
            @Param("completedAt") Instant completedAt);

    AiInvocationRow selectByTenantAndId(@Param("tenantId") String tenantId, @Param("id") UUID id);
}
