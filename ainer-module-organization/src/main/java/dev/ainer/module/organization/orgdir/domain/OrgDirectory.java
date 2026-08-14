package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** 组织目录容器（ADR-0042）：归属 Workspace，不表示法律公司、商家或结算主体。 */
public record OrgDirectory(
        UUID id,
        UUID workspaceId,
        String code,
        String displayName,
        OrgStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
