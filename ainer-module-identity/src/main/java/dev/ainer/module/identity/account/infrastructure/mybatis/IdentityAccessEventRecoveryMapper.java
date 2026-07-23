package dev.ainer.module.identity.account.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityAccessEventRecoveryMapper {

    List<IdentityAccessEventRecoveryRow> selectExhausted(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countExhausted(
            @Param("tenantId") UUID tenantId,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts);

    IdentityAccessEventRecoveryRow selectExhaustedForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("eventId") UUID eventId,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts);

    int expireOpenRequests(@Param("eventId") UUID eventId, @Param("now") Instant now);

    int insertReplayRequest(IdentityAccessEventReplayRequestRow row);

    IdentityAccessEventReplayRequestRow selectReplayRequestForUpdate(
            @Param("tenantId") UUID tenantId, @Param("requestId") UUID requestId);

    int resetExhaustedEvent(
            @Param("tenantId") UUID tenantId,
            @Param("eventId") UUID eventId,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts);

    int markReplayExecuted(
            @Param("requestId") UUID requestId,
            @Param("approvedBy") String approvedBy,
            @Param("executedAt") Instant executedAt);

    int insertOperationAudit(
            @Param("id") UUID id,
            @Param("operationId") UUID operationId,
            @Param("tenantId") UUID tenantId,
            @Param("targetId") UUID targetId,
            @Param("phase") String phase,
            @Param("actorServiceId") String actorServiceId,
            @Param("incidentReference") String incidentReference,
            @Param("occurredAt") Instant occurredAt);
}
