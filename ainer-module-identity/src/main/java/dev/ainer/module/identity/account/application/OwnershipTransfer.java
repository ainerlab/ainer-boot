package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 所有权转移状态机记录。见 ADR-0019 decision 23-28。
 *
 * <p>正常转移使用"双自然人确认"：当前 ACTIVE OWNER 发起 REQUESTED，目标 ACTIVE ADMIN 在
 * 同一 tenant 上下文中接受后原子完成角色交换并进入 EXECUTED。每个 tenant 同时最多一个
 * REQUESTED 转移（由数据库部分唯一索引保证）。
 */
public record OwnershipTransfer(
        UUID id,
        UUID tenantId,
        UUID initiatorSubjectId,
        UUID targetSubjectId,
        OwnershipTransferStatus status,
        String reasonCode,
        String requestId,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant executedAt,
        UUID executedBySubjectId) {

    public OwnershipTransfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(initiatorSubjectId, "initiatorSubjectId");
        Objects.requireNonNull(targetSubjectId, "targetSubjectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean isExpired(Instant now) {
        return status == OwnershipTransferStatus.REQUESTED && expiresAt.isBefore(now);
    }

    public boolean isOutstanding() {
        return status == OwnershipTransferStatus.REQUESTED;
    }
}
