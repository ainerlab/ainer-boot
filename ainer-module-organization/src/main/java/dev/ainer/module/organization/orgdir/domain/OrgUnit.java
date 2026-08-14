package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** 组织单元；ROOT 每 Directory 唯一，父关系在 {@link OrgUnitParent}。 */
public record OrgUnit(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        String code,
        String displayName,
        OrgUnitKind kind,
        OrgStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
