package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** Unit 父关系（半开区间 [validFrom, validUntil)；同一子 Unit 同时最多一条未闭合父关系）。 */
public record OrgUnitParent(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        UUID childUnitId,
        UUID parentUnitId,
        Instant validFrom,
        Instant validUntil,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
