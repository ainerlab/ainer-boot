package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** 绑定到一个 Unit 的组织岗位；orgUnit 创建后不可变（ADR-0042 §4.6）。 */
public record OrgPosition(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        UUID orgUnitId,
        String code,
        String displayName,
        OrgStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
