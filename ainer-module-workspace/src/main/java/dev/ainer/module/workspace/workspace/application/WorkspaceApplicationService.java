package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceName;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkspaceApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAuthorizationAuditService auditService;
    private final Optional<WorkspaceIdentityDirectory> identityDirectory;
    private final Clock clock;

    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceAuthorizationAuditService auditService,
            Optional<WorkspaceIdentityDirectory> identityDirectory,
            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
        this.identityDirectory = identityDirectory;
        this.clock = clock;
    }

    @Transactional
    public Workspace create(AuthenticatedActor actor, CreateWorkspaceCommand command) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.WORKSPACE_CREATE, null, actor.subjectId());
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        TenantId tenantId = tenantId(actor.tenantId());
        SubjectId ownerSubjectId = subjectId(actor.subjectId());
        Workspace workspace = Workspace.create(
                UUID.randomUUID(), tenantId, workspaceName(command.name()), now);
        WorkspaceMember owner = WorkspaceMember.owner(
                tenantId, workspace.id(), ownerSubjectId, now);

        auditAllowed(actor, workspace.id(), actor.subjectId(),
                WorkspaceAuthorizationAction.WORKSPACE_CREATE);
        workspaceRepository.insert(workspace);
        memberRepository.insert(owner);
        return workspace;
    }

    @Transactional(readOnly = true)
    public Workspace get(AuthenticatedActor actor, UUID id) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.WORKSPACE_READ, id, null);
        return requireAccess(actor, id, WorkspaceAuthorizationAction.WORKSPACE_READ, null).workspace();
    }

    @Transactional(readOnly = true)
    public WorkspacePage page(AuthenticatedActor actor, int page, int size) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.WORKSPACE_PAGE, null, null);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_PAGE);
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        return workspaceRepository.findPage(
                tenantId(actor.tenantId()), subjectId(actor.subjectId()), page, size, offset);
    }

    @Transactional
    public Workspace rename(AuthenticatedActor actor, UUID id, String name) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.WORKSPACE_RENAME, id, null);
        WorkspaceAccess access = requireAccess(
                actor, id, WorkspaceAuthorizationAction.WORKSPACE_RENAME, null);
        requireManager(actor, access.member(), id,
                WorkspaceAuthorizationAction.WORKSPACE_RENAME, null);
        Workspace current = access.workspace();
        Workspace renamed = current.rename(workspaceName(name), clock.instant());
        auditAllowed(actor, id, null, WorkspaceAuthorizationAction.WORKSPACE_RENAME);
        if (renamed == current) {
            return current;
        }
        if (!workspaceRepository.update(renamed, current.version())) {
            throw new BusinessException(WorkspaceErrorCode.CONCURRENT_MODIFICATION);
        }
        return renamed;
    }

    @Transactional
    public WorkspaceMember addMember(
            AuthenticatedActor actor, UUID id, AddWorkspaceMemberCommand command) {
        actor = requireActor(actor);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        WorkspaceRole role = requireAssignableRole(command.role());
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_INVITE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                actor, id, WorkspaceAuthorizationAction.MEMBER_INVITE, targetSubjectId.value());
        requireManager(actor, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_INVITE, targetSubjectId.value());
        if (identityDirectory.isPresent()
                && !identityDirectory.get().isActiveMember(access.workspace().tenantId(), targetSubjectId)) {
            throw new BusinessException(WorkspaceErrorCode.IDENTITY_DIRECTORY_MEMBER_NOT_FOUND);
        }
        Instant now = clock.instant();
        WorkspaceMember invitation = WorkspaceMember.invitation(
                access.workspace().tenantId(),
                access.workspace().id(),
                targetSubjectId,
                role,
                subjectId(actor.subjectId()),
                now);

        auditAllowed(actor, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_INVITE);
        memberRepository.insert(invitation);
        return invitation;
    }

    @Transactional
    public WorkspaceMember acceptInvitation(AuthenticatedActor actor, UUID id) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT, id, actor.subjectId());
        TenantId tenantId = tenantId(actor.tenantId());
        SubjectId subjectId = subjectId(actor.subjectId());
        AuthenticatedActor authenticatedActor = actor;
        requireWorkspace(actor, tenantId, id,
                WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT, subjectId.value(), false);
        WorkspaceMember invitation = memberRepository.findByWorkspaceAndSubject(tenantId, id, subjectId)
                .orElseThrow(() -> denied(authenticatedActor, id, subjectId.value(),
                        WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT,
                        WorkspaceErrorCode.INVITATION_NOT_FOUND));
        if (invitation.isActive()) {
            auditAllowed(actor, id, subjectId.value(), WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT);
            return invitation;
        }

        Instant now = clock.instant();
        auditAllowed(actor, id, subjectId.value(), WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT);
        if (!memberRepository.activatePending(tenantId, id, subjectId, now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                invitation.tenantId(), invitation.workspaceId(), invitation.subjectId(), invitation.role(),
                WorkspaceMemberStatus.ACTIVE, invitation.invitedBy(), invitation.createdAt(), now, now);
    }

    @Transactional
    public WorkspaceMember changeMemberRole(
            AuthenticatedActor actor, UUID id, ChangeWorkspaceMemberRoleCommand command) {
        actor = requireActor(actor);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        WorkspaceRole newRole = requireAssignableRole(command.role());
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                actor, id, WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, targetSubjectId.value());
        requireManager(actor, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, targetSubjectId.value());
        WorkspaceMember target = requireTargetMember(
                actor, access.workspace().tenantId(), id, targetSubjectId,
                WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE);
        if (target.role() == WorkspaceRole.OWNER) {
            throw denied(actor, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE,
                    WorkspaceErrorCode.ACCESS_DENIED);
        }

        auditAllowed(actor, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE);
        if (target.role() == newRole) {
            return target;
        }
        Instant now = clock.instant();
        if (!memberRepository.updateRole(
                target.tenantId(), id, targetSubjectId, target.role(), newRole, now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                target.tenantId(), target.workspaceId(), target.subjectId(), newRole, target.status(),
                target.invitedBy(), target.createdAt(), target.activatedAt(), now);
    }

    @Transactional
    public void removeMember(
            AuthenticatedActor actor, UUID id, RemoveWorkspaceMemberCommand command) {
        actor = requireActor(actor);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_REMOVE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                actor, id, WorkspaceAuthorizationAction.MEMBER_REMOVE, targetSubjectId.value());
        requireManager(actor, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_REMOVE, targetSubjectId.value());
        WorkspaceMember target = requireTargetMember(
                actor, access.workspace().tenantId(), id, targetSubjectId,
                WorkspaceAuthorizationAction.MEMBER_REMOVE);
        if (target.role() == WorkspaceRole.OWNER) {
            throw denied(actor, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.MEMBER_REMOVE,
                    WorkspaceErrorCode.ACCESS_DENIED);
        }

        auditAllowed(actor, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_REMOVE);
        if (!memberRepository.deleteNonOwner(target.tenantId(), id, targetSubjectId)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
    }

    @Transactional
    public WorkspaceMember transferOwnership(
            AuthenticatedActor actor, UUID id, TransferWorkspaceOwnershipCommand command) {
        actor = requireActor(actor);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.newOwnerSubjectId());
        requireAuthority(actor, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER, id, targetSubjectId.value());
        TenantId tenantId = tenantId(actor.tenantId());
        Workspace workspace = requireWorkspace(
                actor, tenantId, id, WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                targetSubjectId.value(), true);
        SubjectId actorSubjectId = subjectId(actor.subjectId());
        AuthenticatedActor authenticatedActor = actor;
        WorkspaceMember currentOwner = memberRepository.findByWorkspaceAndSubject(
                        tenantId, id, actorSubjectId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> denied(authenticatedActor, id, targetSubjectId.value(),
                        WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                        WorkspaceErrorCode.NOT_FOUND));
        if (currentOwner.role() != WorkspaceRole.OWNER) {
            throw denied(actor, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                    WorkspaceErrorCode.ACCESS_DENIED);
        }
        if (actorSubjectId.equals(targetSubjectId)) {
            auditAllowed(actor, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
            return currentOwner;
        }
        WorkspaceMember target = requireTargetMember(
                actor, workspace.tenantId(), id, targetSubjectId,
                WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
        if (!target.isActive()) {
            throw denied(actor, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                    WorkspaceErrorCode.MEMBER_NOT_ACTIVE);
        }

        Instant now = clock.instant();
        auditAllowed(actor, id, targetSubjectId.value(),
                WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
        if (!memberRepository.demoteOwner(tenantId, id, actorSubjectId, now)
                || !memberRepository.promoteActiveMemberToOwner(
                        tenantId, id, targetSubjectId, target.role(), now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                target.tenantId(), target.workspaceId(), target.subjectId(), WorkspaceRole.OWNER,
                target.status(), target.invitedBy(), target.createdAt(), target.activatedAt(), now);
    }

    @Transactional(readOnly = true)
    public WorkspaceAuthorizationAuditPage authorizationAudits(
            AuthenticatedActor actor, UUID id, int page, int size) {
        actor = requireActor(actor);
        requireAuthority(actor, WorkspaceAuthorities.AUDIT_READ,
                WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, id, null);
        WorkspaceAccess access = requireAccess(
                actor, id, WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, null);
        requireManager(actor, access.member(), id,
                WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, null);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_PAGE);
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        auditAllowed(actor, id, null, WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ);
        return auditService.findPage(access.workspace().tenantId(), id, page, size, offset);
    }

    private WorkspaceAccess requireAccess(
            AuthenticatedActor actor,
            UUID id,
            WorkspaceAuthorizationAction action,
            String targetSubjectId) {
        Objects.requireNonNull(id, "id");
        TenantId tenantId = tenantId(actor.tenantId());
        SubjectId subjectId = subjectId(actor.subjectId());
        Workspace workspace = requireWorkspace(
                actor, tenantId, id, action, targetSubjectId, false);
        WorkspaceMember member = memberRepository.findByWorkspaceAndSubject(tenantId, id, subjectId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> denied(
                        actor, id, targetSubjectId, action, WorkspaceErrorCode.NOT_FOUND));
        return new WorkspaceAccess(workspace, member);
    }

    private Workspace requireWorkspace(
            AuthenticatedActor actor,
            TenantId tenantId,
            UUID id,
            WorkspaceAuthorizationAction action,
            String targetSubjectId,
            boolean forUpdate) {
        return (forUpdate
                ? workspaceRepository.findByIdForUpdate(tenantId, id)
                : workspaceRepository.findById(tenantId, id))
                .orElseThrow(() -> denied(
                        actor, id, targetSubjectId, action, WorkspaceErrorCode.NOT_FOUND));
    }

    private WorkspaceMember requireTargetMember(
            AuthenticatedActor actor,
            TenantId tenantId,
            UUID workspaceId,
            SubjectId targetSubjectId,
            WorkspaceAuthorizationAction action) {
        return memberRepository.findByWorkspaceAndSubject(tenantId, workspaceId, targetSubjectId)
                .orElseThrow(() -> denied(
                        actor, workspaceId, targetSubjectId.value(), action, WorkspaceErrorCode.NOT_FOUND));
    }

    private void requireManager(
            AuthenticatedActor actor,
            WorkspaceMember member,
            UUID workspaceId,
            WorkspaceAuthorizationAction action,
            String targetSubjectId) {
        if (!member.role().canManageWorkspace()) {
            throw denied(actor, workspaceId, targetSubjectId, action, WorkspaceErrorCode.ACCESS_DENIED);
        }
    }

    private void requireAuthority(
            AuthenticatedActor actor,
            String authority,
            WorkspaceAuthorizationAction action,
            UUID workspaceId,
            String targetSubjectId) {
        if (!actor.hasAuthority(authority)) {
            throw denied(actor, workspaceId, targetSubjectId, action, StandardErrorCode.FORBIDDEN);
        }
    }

    private BusinessException denied(
            AuthenticatedActor actor,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action,
            ErrorCode reason) {
        auditService.record(
                actor, workspaceId, targetSubjectId, action,
                WorkspaceAuthorizationDecision.DENIED, reason);
        return new BusinessException(reason);
    }

    private void auditAllowed(
            AuthenticatedActor actor,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action) {
        auditService.record(
                actor, workspaceId, targetSubjectId, action,
                WorkspaceAuthorizationDecision.ALLOWED, StandardErrorCode.OK);
    }

    private AuthenticatedActor requireActor(AuthenticatedActor actor) {
        return Objects.requireNonNull(actor, "actor");
    }

    private WorkspaceRole requireAssignableRole(WorkspaceRole role) {
        role = Objects.requireNonNull(role, "role");
        if (!role.canBeAssignedByMemberEndpoint()) {
            throw new BusinessException(WorkspaceErrorCode.ROLE_NOT_ASSIGNABLE);
        }
        return role;
    }

    private WorkspaceName workspaceName(String value) {
        try {
            return new WorkspaceName(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_NAME);
        }
    }

    private SubjectId subjectId(String value) {
        try {
            return new SubjectId(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_SUBJECT);
        }
    }

    private TenantId tenantId(String value) {
        try {
            return new TenantId(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
        }
    }

    private record WorkspaceAccess(Workspace workspace, WorkspaceMember member) {
    }
}
