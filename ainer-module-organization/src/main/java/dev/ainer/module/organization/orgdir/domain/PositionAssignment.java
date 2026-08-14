package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** 岗位任职；引用的 UnitAssignment 必须同 Engagement 且落同一 Unit（复合 FK + 服务层）。 */
public record PositionAssignment(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        UUID positionId,
        UUID engagementId,
        UUID unitAssignmentId,
        UUID orgUnitId,
        AssignmentKind kind,
        Instant validFrom,
        Instant validUntil,
        OrgStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
