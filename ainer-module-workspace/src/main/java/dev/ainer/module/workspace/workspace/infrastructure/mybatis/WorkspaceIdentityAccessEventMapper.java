package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityAccessEventType;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

public interface WorkspaceIdentityAccessEventMapper {

    int insertReceipt(
            @Param("eventId") UUID eventId,
            @Param("eventType") WorkspaceIdentityAccessEventType eventType,
            @Param("tenantId") UUID tenantId,
            @Param("subjectId") UUID subjectId,
            @Param("payloadVersion") int payloadVersion,
            @Param("occurredAt") Instant occurredAt,
            @Param("receivedAt") Instant receivedAt);

    int revokeExistingMemberships(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("occurredAt") Instant occurredAt,
            @Param("receivedAt") Instant receivedAt);

    int updateAffectedMemberships(
            @Param("eventId") UUID eventId,
            @Param("affectedMemberships") int affectedMemberships);

    Integer selectAffectedMemberships(@Param("eventId") UUID eventId);
}
