package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 无主 Workspace 的 OWNER 恢复服务，实现 request/approve 两阶段流程。
 *
 * <p>只有当 Workspace 存在 REVOKED OWNER 且没有任何 ACTIVE OWNER 时才允许发起恢复；
 * 申请与批准必须来自不同的 SERVICE 主体，两阶段都要重新校验目标仍是 ACTIVE 的非 OWNER
 * 成员。恢复在锁定 Workspace 行（{@code FOR UPDATE}）的事务内提升新 OWNER，并写入
 * 安全操作审计；请求带 TTL，过期后不可再批准。
 */
@Service
public class WorkspaceOwnerRecoveryService {

    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceOwnerRecoveryRepository recoveryRepository;
    private final WorkspaceSecurityOperationAuditRepository operationAuditRepository;
    private final Clock clock;

    public WorkspaceOwnerRecoveryService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceOwnerRecoveryRepository recoveryRepository,
            WorkspaceSecurityOperationAuditRepository operationAuditRepository,
            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.recoveryRepository = recoveryRepository;
        this.operationAuditRepository = operationAuditRepository;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceOwnerRecoveryRequest requestRecovery(
            String requesterServiceId,
            UUID workspaceId,
            SubjectId newOwnerSubjectId,
            String incidentReference,
            Duration approvalTtl) {
        requesterServiceId = requireSafe(requesterServiceId);
        incidentReference = requireSafe(incidentReference);
        requireTtl(approvalTtl);
        Instant now = clock.instant();
        recoveryRepository.expireOpenRequests(workspaceId, now);
        requireRecoverable(workspaceId, newOwnerSubjectId);
        WorkspaceOwnerRecoveryRequest request = new WorkspaceOwnerRecoveryRequest(
                dev.ainer.core.uuid.Uuidv7.generate(), workspaceId, newOwnerSubjectId,
                requesterServiceId, null, incidentReference, "REQUESTED",
                now, now.plus(approvalTtl), null);
        recoveryRepository.insert(request);
        record(request, "REQUESTED", requesterServiceId, now);
        return request;
    }

    @Transactional
    public WorkspaceOwnerRecoveryRequest approveAndExecute(
            String approverServiceId, UUID requestId) {
        approverServiceId = requireSafe(approverServiceId);
        WorkspaceOwnerRecoveryRequest request = recoveryRepository.findForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_FOUND));
        if (!"REQUESTED".equals(request.status())) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_CONFLICT);
        }
        if (request.requestedBy().equals(approverServiceId)) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_APPROVER_MUST_DIFFER);
        }
        Instant now = clock.instant();
        if (!now.isBefore(request.expiresAt())) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_EXPIRED);
        }
        WorkspaceMember target = requireRecoverable(
                request.workspaceId(), request.newOwnerSubjectId());
        if (!memberRepository.promoteActiveMemberToOwner(
                request.workspaceId(), request.newOwnerSubjectId(), target.role(), now)
                || !recoveryRepository.markExecuted(request.id(), approverServiceId, now)) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_CONFLICT);
        }
        record(request, "EXECUTED", approverServiceId, now);
        return new WorkspaceOwnerRecoveryRequest(
                request.id(), request.workspaceId(), request.newOwnerSubjectId(),
                request.requestedBy(), approverServiceId, request.incidentReference(), "EXECUTED",
                request.requestedAt(), request.expiresAt(), now);
    }

    private WorkspaceMember requireRecoverable(
            UUID workspaceId, SubjectId newOwnerSubjectId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(newOwnerSubjectId, "newOwnerSubjectId");
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_FOUND));
        if (memberRepository.hasActiveOwner(workspace.id())
                || !memberRepository.hasRevokedOwner(workspace.id())) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_REQUIRED);
        }
        WorkspaceMember target = memberRepository.findByWorkspaceAndSubject(
                        workspace.id(), newOwnerSubjectId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_TARGET_NOT_ACTIVE));
        if (target.role() == WorkspaceRole.OWNER) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_REQUIRED);
        }
        return target;
    }

    private void record(
            WorkspaceOwnerRecoveryRequest request,
            String phase,
            String actorServiceId,
            Instant occurredAt) {
        operationAuditRepository.insert(new WorkspaceSecurityOperationAudit(
                dev.ainer.core.uuid.Uuidv7.generate(), request.id(), request.workspaceId(),
                request.newOwnerSubjectId(), "OWNER_RECOVERY", phase, actorServiceId,
                request.incidentReference(), null, occurredAt));
    }

    private String requireSafe(String value) {
        value = Objects.requireNonNull(value, "value").trim();
        if (!SAFE_REFERENCE.matcher(value).matches()) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_OWNER_RECOVERY_REQUEST);
        }
        return value;
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || !ttl.isPositive() || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Workspace owner recovery TTL is invalid");
        }
    }
}
