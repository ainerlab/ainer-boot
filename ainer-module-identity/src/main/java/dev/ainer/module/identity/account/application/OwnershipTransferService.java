package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 所有权转移用例。见 ADR-0019 decision 23-30。
 *
 * <p>正常转移使用"双自然人确认"状态机：当前 ACTIVE OWNER 发起 REQUESTED，目标 ACTIVE ADMIN
 * 在同 tenant 上下文中接受后原子完成角色交换并进入 EXECUTED。每个 tenant 同时最多一个 REQUESTED
 * 转移。执行事务锁定双方 membership、再次校验 ACTIVE 角色、交换 OWNER↔ADMIN、写入操作审计并为
 * 双方写入 access event/outbox 使旧角色 Token 进入撤销链路。
 */
@Service
public class OwnershipTransferService {

    private static final String TRANSFER_AUTHORITY =
            "SCOPE_" + AinerSecurityScopes.TENANT_OWNERSHIP_TRANSFER;
    private static final Pattern SAFE_AUDIT_REFERENCE =
            Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final IdentityRepository repository;
    private final Clock clock;

    public OwnershipTransferService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public OwnershipTransfer initiateTransfer(
            AuthenticatedActor actor,
            UUID tenantId,
            UUID targetSubjectId,
            String reasonCode,
            String requestId) {
        UUID initiatorSubjectId = requireInitiator(actor, tenantId);
        requireReference(reasonCode);
        requireReference(requestId);
        Objects.requireNonNull(targetSubjectId, "targetSubjectId");

        // 目标必须是同 tenant 的 ACTIVE ADMIN
        IdentityDirectoryEntry target = repository.findActiveDirectoryEntryForUpdate(tenantId, targetSubjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_TARGET_INELIGIBLE));
        if (target.role() != TenantRole.ADMIN) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_TARGET_INELIGIBLE);
        }
        if (target.subjectId().equals(initiatorSubjectId)) {
            throw new BusinessException(IdentityErrorCode.INVALID_OWNERSHIP_TRANSFER_REQUEST);
        }

        Instant now = clock.instant();
        OwnershipTransfer transfer = new OwnershipTransfer(
                UUID.randomUUID(),
                tenantId,
                initiatorSubjectId,
                targetSubjectId,
                OwnershipTransferStatus.REQUESTED,
                reasonCode,
                requestId,
                now.plus(DEFAULT_TTL),
                now,
                now,
                null,
                null);
        try {
            repository.insertOwnershipTransfer(transfer);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_OUTSTANDING_CONFLICT);
        }
        return transfer;
    }

    @Transactional
    public OwnershipTransfer acceptTransfer(
            AuthenticatedActor actor,
            UUID tenantId,
            UUID transferId,
            String reasonCode,
            String requestId) {
        UUID acceptorSubjectId = requireAcceptor(actor, tenantId);
        requireReference(reasonCode);
        requireReference(requestId);

        OwnershipTransfer transfer = repository.findOwnershipTransferForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND));
        if (!transfer.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND);
        }
        if (transfer.status() != OwnershipTransferStatus.REQUESTED) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        if (transfer.isExpired(clock.instant())) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        // 只有转移目标本人可以接受
        if (!transfer.targetSubjectId().equals(acceptorSubjectId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_ACCEPTOR_MUST_BE_TARGET);
        }

        // 锁定双方 membership 并再次校验角色
        TenantMembership initiatorMembership =
                repository.findMembershipForUpdate(tenantId, transfer.initiatorSubjectId())
                        .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT));
        TenantMembership targetMembership =
                repository.findMembershipForUpdate(tenantId, transfer.targetSubjectId())
                        .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT));
        if (initiatorMembership.role() != TenantRole.OWNER
                || initiatorMembership.status() != IdentityStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        if (targetMembership.role() != TenantRole.ADMIN
                || targetMembership.status() != IdentityStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }

        Instant now = clock.instant();
        // 先降原 OWNER 为 ADMIN，再升目标为 OWNER
        if (!repository.updateMembershipRole(tenantId, transfer.initiatorSubjectId(),
                TenantRole.ADMIN.name(), now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        if (!repository.updateMembershipRole(tenantId, transfer.targetSubjectId(),
                TenantRole.OWNER.name(), now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        if (!repository.completeOwnershipTransfer(
                transferId, tenantId, acceptorSubjectId, now, now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        // 审计 + 撤销事件（双方）同事务
        repository.insertMemberAudit(
                tenantId, acceptorSubjectId, transfer.initiatorSubjectId(),
                "OWNERSHIP_TRANSFERRED", TenantRole.OWNER.name(),
                reasonCode, requestId, now);
        repository.insertAccessEvent(IdentityAccessEvent.membershipRoleChanged(
                tenantId, transfer.initiatorSubjectId(), now));
        repository.insertAccessEvent(IdentityAccessEvent.membershipRoleChanged(
                tenantId, transfer.targetSubjectId(), now));

        return repository.findOwnershipTransfer(transferId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT));
    }

    @Transactional
    public OwnershipTransfer cancelTransfer(
            AuthenticatedActor actor,
            UUID tenantId,
            UUID transferId,
            String reasonCode,
            String requestId) {
        UUID callerSubjectId = requireInitiator(actor, tenantId);
        requireReference(reasonCode);
        requireReference(requestId);

        OwnershipTransfer transfer = repository.findOwnershipTransferForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND));
        if (!transfer.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND);
        }
        if (transfer.status() != OwnershipTransferStatus.REQUESTED) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        // 发起者或目标 ADMIN 都可以取消
        if (!transfer.initiatorSubjectId().equals(callerSubjectId)
                && !transfer.targetSubjectId().equals(callerSubjectId)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        Instant now = clock.instant();
        if (!repository.cancelOwnershipTransfer(transferId, tenantId, now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT);
        }
        return repository.findOwnershipTransfer(transferId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_STATE_CONFLICT));
    }

    @Transactional(readOnly = true)
    public OwnershipTransfer getTransfer(AuthenticatedActor actor, UUID tenantId, UUID transferId) {
        requireParticipant(actor, tenantId);
        OwnershipTransfer transfer = repository.findOwnershipTransfer(transferId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND));
        if (!transfer.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_TRANSFER_NOT_FOUND);
        }
        return transfer;
    }

    private UUID requireInitiator(AuthenticatedActor actor, UUID tenantId) {
        UUID subjectId = requireTransferCapability(actor, tenantId);
        IdentityDirectoryEntry caller = repository.findActiveDirectoryEntryForUpdate(tenantId, subjectId)
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        if (caller.role() != TenantRole.OWNER) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return subjectId;
    }

    private UUID requireAcceptor(AuthenticatedActor actor, UUID tenantId) {
        UUID subjectId = requireTransferCapability(actor, tenantId);
        IdentityDirectoryEntry caller = repository.findActiveDirectoryEntryForUpdate(tenantId, subjectId)
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        if (caller.role() != TenantRole.ADMIN) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return subjectId;
    }

    private UUID requireParticipant(AuthenticatedActor actor, UUID tenantId) {
        UUID subjectId = requireTransferCapability(actor, tenantId);
        repository.findActiveDirectoryEntry(tenantId, subjectId)
                .orElseThrow(() -> new BusinessException(StandardErrorCode.FORBIDDEN));
        return subjectId;
    }

    private static UUID requireTransferCapability(AuthenticatedActor actor, UUID tenantId) {
        if (actor == null || !actor.isUser() || !actor.hasAuthority(TRANSFER_AUTHORITY)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        UUID actorTenantId = trustedUuid(actor.tenantId());
        UUID actorSubjectId = trustedUuid(actor.subjectId());
        if (!tenantId.equals(actorTenantId)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return actorSubjectId;
    }

    private static void requireReference(String value) {
        if (value == null || !SAFE_AUDIT_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(IdentityErrorCode.INVALID_OWNERSHIP_TRANSFER_REQUEST);
        }
    }

    private static UUID trustedUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
