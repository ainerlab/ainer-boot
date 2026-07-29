package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * OWNER 丢失恢复记录。见 ADR-0019 decision 30。
 *
 * <p>恢复使用 tenantless SERVICE 的独立 request/approve credential，不同 service subject；
 * 只能把现有 ACTIVE ADMIN 提升为 OWNER，不恢复被禁用主体。与正常转移不共用端点或授权规则。
 */
public record OwnershipRecovery(
        UUID id,
        UUID tenantId,
        UUID targetSubjectId,
        OwnershipTransferStatus status,
        String requesterServiceId,
        String approverServiceId,
        String incidentReference,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant executedAt) {

    public OwnershipRecovery {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetSubjectId, "targetSubjectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requesterServiceId, "requesterServiceId");
        Objects.requireNonNull(incidentReference, "incidentReference");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
