package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
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
            TenantId tenantId,
            UUID workspaceId,
            SubjectId newOwnerSubjectId,
            String incidentReference,
            Duration approvalTtl) {
        requesterServiceId = requireSafe(requesterServiceId);
        incidentReference = requireSafe(incidentReference);
        requireTtl(approvalTtl);
        Instant now = clock.instant();
        recoveryRepository.expireOpenRequests(tenantId, workspaceId, now);
        requireRecoverable(tenantId, workspaceId, newOwnerSubjectId);
        WorkspaceOwnerRecoveryRequest request = new WorkspaceOwnerRecoveryRequest(
                UUID.randomUUID(), tenantId, workspaceId, newOwnerSubjectId,
                requesterServiceId, null, incidentReference, "REQUESTED",
                now, now.plus(approvalTtl), null);
        recoveryRepository.insert(request);
        record(request, "REQUESTED", requesterServiceId, now);
        return request;
    }

    @Transactional
    public WorkspaceOwnerRecoveryRequest approveAndExecute(
            String approverServiceId, TenantId tenantId, UUID requestId) {
        approverServiceId = requireSafe(approverServiceId);
        WorkspaceOwnerRecoveryRequest request = recoveryRepository.findForUpdate(tenantId, requestId)
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
                tenantId, request.workspaceId(), request.newOwnerSubjectId());
        if (!memberRepository.promoteActiveMemberToOwner(
                tenantId, request.workspaceId(), request.newOwnerSubjectId(), target.role(), now)
                || !recoveryRepository.markExecuted(request.id(), approverServiceId, now)) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_CONFLICT);
        }
        record(request, "EXECUTED", approverServiceId, now);
        return new WorkspaceOwnerRecoveryRequest(
                request.id(), request.tenantId(), request.workspaceId(), request.newOwnerSubjectId(),
                request.requestedBy(), approverServiceId, request.incidentReference(), "EXECUTED",
                request.requestedAt(), request.expiresAt(), now);
    }

    private WorkspaceMember requireRecoverable(
            TenantId tenantId, UUID workspaceId, SubjectId newOwnerSubjectId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(newOwnerSubjectId, "newOwnerSubjectId");
        Workspace workspace = workspaceRepository.findByIdForUpdate(tenantId, workspaceId)
                .orElseThrow(() -> new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_FOUND));
        if (memberRepository.hasActiveOwner(workspace.tenantId(), workspace.id())
                || !memberRepository.hasRevokedOwner(workspace.tenantId(), workspace.id())) {
            throw new BusinessException(WorkspaceErrorCode.OWNER_RECOVERY_NOT_REQUIRED);
        }
        WorkspaceMember target = memberRepository.findByWorkspaceAndSubject(
                        workspace.tenantId(), workspace.id(), newOwnerSubjectId)
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
                UUID.randomUUID(), request.id(), request.tenantId(), request.workspaceId(),
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
