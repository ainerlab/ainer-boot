package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/** 组织目录变更审计（append-only，同事务写入）。 */
public record OrgChangeAudit(
        UUID id,
        String entityType,
        UUID entityId,
        String operation,
        String actorIssuer,
        String actorType,
        String actorId,
        String requestId,
        Instant occurredAt) {
}
