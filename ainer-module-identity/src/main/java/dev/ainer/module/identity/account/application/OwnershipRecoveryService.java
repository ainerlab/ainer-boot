package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.OwnershipTransferStatus;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
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
 * OWNER 丢失恢复。见 ADR-0019 decision 30。
 *
 * <p>恢复使用 tenantless SERVICE 的独立 request/approve credential，不同 service subject。
 * 只能把现有 ACTIVE ADMIN 提升为 OWNER 并降原 OWNER 为 ADMIN，不恢复被禁用主体。
 * 与正常转移不共用端点或授权规则。
 */
@Service
public class OwnershipRecoveryService {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final IdentityRepository repository;
    private final Clock clock;

    public OwnershipRecoveryService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public OwnershipRecovery requestRecovery(
            String requesterServiceId,
            UUID tenantId,
            UUID targetSubjectId,
            String incidentReference) {
        requesterServiceId = requireReference(requesterServiceId, "requester service id");
        incidentReference = requireReference(incidentReference, "incident reference");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetSubjectId, "targetSubjectId");

        // 目标必须是同 tenant 的 ACTIVE ADMIN
        IdentityDirectoryEntry target = repository.findActiveDirectoryEntryForUpdate(tenantId, targetSubjectId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_TARGET_INELIGIBLE));
        if (target.role() != TenantRole.ADMIN) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_TARGET_INELIGIBLE);
        }

        Instant now = clock.instant();
        OwnershipRecovery recovery = new OwnershipRecovery(
                UUID.randomUUID(),
                tenantId,
                targetSubjectId,
                OwnershipTransferStatus.REQUESTED,
                requesterServiceId,
                null,
                incidentReference,
                now.plus(DEFAULT_TTL),
                now,
                now,
                null);
        try {
            repository.insertOwnershipRecovery(recovery);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        repository.insertSecurityOperationAudit(
                recovery.id(), tenantId, targetSubjectId, "OWNERSHIP_RECOVERY",
                "REQUESTED", requesterServiceId, incidentReference, now);
        return recovery;
    }

    @Transactional
    public OwnershipRecovery approveAndExecute(
            String approverServiceId,
            UUID tenantId,
            UUID recoveryId) {
        approverServiceId = requireReference(approverServiceId, "approver service id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recoveryId, "recoveryId");

        OwnershipRecovery recovery = repository.findOwnershipRecoveryForUpdate(recoveryId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND));
        if (!recovery.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND);
        }
        if (recovery.status() != OwnershipTransferStatus.REQUESTED) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        if (recovery.requesterServiceId().equals(approverServiceId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_APPROVER_MUST_DIFFER);
        }
        if (!clock.instant().isBefore(recovery.expiresAt())) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }

        // 锁定目标 ADMIN 与当前 OWNER
        TenantMembership targetMembership =
                repository.findMembershipForUpdate(tenantId, recovery.targetSubjectId())
                        .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_TARGET_INELIGIBLE));
        if (targetMembership.role() != TenantRole.ADMIN
                || targetMembership.status() != IdentityStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_TARGET_INELIGIBLE);
        }

        // 查找当前 ACTIVE OWNER 并降级
        java.util.List<UUID> ownerTenantIds = repository.findActiveMembershipTenantIds(recovery.targetSubjectId());
        // 在目标 tenant 中查找当前 OWNER（用 membership 查询）
        findAndDemoteCurrentOwner(tenantId, recovery.targetSubjectId());

        Instant now = clock.instant();
        // 提升目标为 OWNER
        if (!repository.updateMembershipRole(tenantId, recovery.targetSubjectId(),
                TenantRole.OWNER.name(), now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        if (!repository.executeOwnershipRecovery(
                recoveryId, tenantId, approverServiceId, now, now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        // 审计 + 撤销事件
        repository.insertSecurityOperationAudit(
                recoveryId, tenantId, recovery.targetSubjectId(), "OWNERSHIP_RECOVERY",
                "EXECUTED", approverServiceId, recovery.incidentReference(), now);
        repository.insertAccessEvent(IdentityAccessEvent.membershipRoleChanged(
                tenantId, recovery.targetSubjectId(), now));
        return repository.findOwnershipRecovery(recoveryId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT));
    }

    @Transactional
    public OwnershipRecovery cancelRecovery(
            String requesterServiceId,
            UUID tenantId,
            UUID recoveryId) {
        requesterServiceId = requireReference(requesterServiceId, "requester service id");
        OwnershipRecovery recovery = repository.findOwnershipRecoveryForUpdate(recoveryId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND));
        if (!recovery.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND);
        }
        if (recovery.status() != OwnershipTransferStatus.REQUESTED) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        if (!recovery.requesterServiceId().equals(requesterServiceId)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        Instant now = clock.instant();
        if (!repository.cancelOwnershipRecovery(recoveryId, tenantId, now)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
        }
        return repository.findOwnershipRecovery(recoveryId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT));
    }

    @Transactional(readOnly = true)
    public OwnershipRecovery getRecovery(String serviceId, UUID tenantId, UUID recoveryId) {
        requireReference(serviceId, "service id");
        OwnershipRecovery recovery = repository.findOwnershipRecovery(recoveryId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND));
        if (!recovery.tenantId().equals(tenantId)) {
            throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_NOT_FOUND);
        }
        return recovery;
    }

    private void findAndDemoteCurrentOwner(UUID tenantId, UUID excludeSubjectId) {
        // 查找当前 tenant 的 ACTIVE OWNER 并降为 ADMIN
        // 使用 directory 查询遍历成员找到 OWNER
        int offset = 0;
        int limit = 100;
        while (true) {
            java.util.List<IdentityDirectoryEntry> members =
                    repository.listMembersByTenant(tenantId, offset, limit);
            if (members.isEmpty()) {
                break;
            }
            for (IdentityDirectoryEntry member : members) {
                if (member.role() == TenantRole.OWNER && !member.subjectId().equals(excludeSubjectId)) {
                    Instant now = clock.instant();
                    if (!repository.updateMembershipRole(tenantId, member.subjectId(),
                            TenantRole.ADMIN.name(), now)) {
                        throw new BusinessException(IdentityErrorCode.OWNERSHIP_RECOVERY_STATE_CONFLICT);
                    }
                    repository.insertAccessEvent(IdentityAccessEvent.membershipRoleChanged(
                            tenantId, member.subjectId(), now));
                }
            }
            if (members.size() < limit) {
                break;
            }
            offset += limit;
        }
    }

    private static String requireReference(String value, String name) {
        if (value == null || !SAFE_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(IdentityErrorCode.INVALID_OWNERSHIP_TRANSFER_REQUEST);
        }
        return value;
    }
}
