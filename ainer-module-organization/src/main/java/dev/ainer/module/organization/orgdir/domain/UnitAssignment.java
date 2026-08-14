package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** Engagement 到 Unit 的任职分配；有效期必须包含于父 Engagement。 */
public record UnitAssignment(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        UUID engagementId,
        UUID orgUnitId,
        AssignmentKind kind,
        Instant validFrom,
        Instant validUntil,
        OrgStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
