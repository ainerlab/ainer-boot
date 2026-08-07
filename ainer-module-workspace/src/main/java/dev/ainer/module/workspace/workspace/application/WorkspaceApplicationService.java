package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceName;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.security.token.AuthenticatedPrincipal;
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
    public Workspace create(AuthenticatedPrincipal principal, CreateWorkspaceCommand command) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.WORKSPACE_CREATE, null, principalSubject(principal));
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        SubjectId ownerSubjectId = subjectId(principal.subjectId());
        Workspace workspace = Workspace.create(
                UUID.randomUUID(), workspaceName(command.name()), now);
        WorkspaceMember owner = WorkspaceMember.owner(workspace.id(), ownerSubjectId, now);

        auditAllowed(principal, workspace.id(), principal.subjectId(),
                WorkspaceAuthorizationAction.WORKSPACE_CREATE);
        workspaceRepository.insert(workspace);
        memberRepository.insert(owner);
        return workspace;
    }

    @Transactional(readOnly = true)
    public Workspace get(AuthenticatedPrincipal principal, UUID id) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.WORKSPACE_READ, id, null);
        return requireAccess(principal, id, WorkspaceAuthorizationAction.WORKSPACE_READ, null).workspace();
    }

    @Transactional(readOnly = true)
    public WorkspacePage page(AuthenticatedPrincipal principal, int page, int size) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.WORKSPACE_PAGE, null, null);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_PAGE);
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        return workspaceRepository.findPage(subjectId(principal.subjectId()), page, size, offset);
    }

    @Transactional
    public Workspace rename(AuthenticatedPrincipal principal, UUID id, String name) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.WORKSPACE_RENAME, id, null);
        WorkspaceAccess access = requireAccess(
                principal, id, WorkspaceAuthorizationAction.WORKSPACE_RENAME, null);
        requireManager(principal, access.member(), id,
                WorkspaceAuthorizationAction.WORKSPACE_RENAME, null);
        Workspace current = access.workspace();
        Workspace renamed = current.rename(workspaceName(name), clock.instant());
        auditAllowed(principal, id, null, WorkspaceAuthorizationAction.WORKSPACE_RENAME);
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
            AuthenticatedPrincipal principal, UUID id, AddWorkspaceMemberCommand command) {
        principal = requireHuman(principal);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        WorkspaceRole role = requireAssignableRole(command.role());
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_INVITE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                principal, id, WorkspaceAuthorizationAction.MEMBER_INVITE, targetSubjectId.value());
        requireManager(principal, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_INVITE, targetSubjectId.value());
        if (identityDirectory.isPresent()
                && !identityDirectory.get().isActiveHumanAccount(targetSubjectId)) {
            throw new BusinessException(WorkspaceErrorCode.IDENTITY_DIRECTORY_MEMBER_NOT_FOUND);
        }
        Instant now = clock.instant();
        WorkspaceMember invitation = WorkspaceMember.invitation(
                access.workspace().id(),
                targetSubjectId,
                role,
                subjectId(principal.subjectId()),
                now);

        auditAllowed(principal, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_INVITE);
        memberRepository.insert(invitation);
        return invitation;
    }

    @Transactional
    public WorkspaceMember acceptInvitation(AuthenticatedPrincipal principal, UUID id) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.READ,
                WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT, id, principal.subjectId());
        SubjectId subjectId = subjectId(principal.subjectId());
        Workspace workspace = requireWorkspace(
                principal, id, WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT, subjectId.value(), false);
        AuthenticatedPrincipal authenticatedPrincipal = principal;
        WorkspaceMember invitation = memberRepository.findByWorkspaceAndSubject(id, subjectId)
                .orElseThrow(() -> denied(authenticatedPrincipal, id, subjectId.value(),
                        WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT,
                        WorkspaceErrorCode.INVITATION_NOT_FOUND));
        if (invitation.isActive()) {
            auditAllowed(principal, id, subjectId.value(), WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT);
            return invitation;
        }

        Instant now = clock.instant();
        auditAllowed(principal, id, subjectId.value(), WorkspaceAuthorizationAction.MEMBERSHIP_ACCEPT);
        if (!memberRepository.activatePending(id, subjectId, now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                workspace.id(), invitation.subjectId(), invitation.role(),
                WorkspaceMemberStatus.ACTIVE, invitation.invitedBy(),
                invitation.createdAt(), now, now);
    }

    @Transactional
    public WorkspaceMember changeMemberRole(
            AuthenticatedPrincipal principal, UUID id, ChangeWorkspaceMemberRoleCommand command) {
        principal = requireHuman(principal);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        WorkspaceRole newRole = requireAssignableRole(command.role());
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                principal, id, WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, targetSubjectId.value());
        requireManager(principal, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE, targetSubjectId.value());
        WorkspaceMember target = requireTargetMember(
                principal, id, targetSubjectId, WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE);
        if (target.role() == WorkspaceRole.OWNER) {
            throw denied(principal, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE,
                    WorkspaceErrorCode.ACCESS_DENIED);
        }

        auditAllowed(principal, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_ROLE_CHANGE);
        if (target.role() == newRole) {
            return target;
        }
        Instant now = clock.instant();
        if (!memberRepository.updateRole(
                id, targetSubjectId, target.role(), newRole, now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                target.workspaceId(), target.subjectId(), newRole, target.status(),
                target.invitedBy(), target.createdAt(), target.activatedAt(), now);
    }

    @Transactional
    public void removeMember(
            AuthenticatedPrincipal principal, UUID id, RemoveWorkspaceMemberCommand command) {
        principal = requireHuman(principal);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.subjectId());
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.MEMBER_REMOVE, id, targetSubjectId.value());
        WorkspaceAccess access = requireAccess(
                principal, id, WorkspaceAuthorizationAction.MEMBER_REMOVE, targetSubjectId.value());
        requireManager(principal, access.member(), id,
                WorkspaceAuthorizationAction.MEMBER_REMOVE, targetSubjectId.value());
        WorkspaceMember target = requireTargetMember(
                principal, id, targetSubjectId, WorkspaceAuthorizationAction.MEMBER_REMOVE);
        if (target.role() == WorkspaceRole.OWNER) {
            throw denied(principal, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.MEMBER_REMOVE, WorkspaceErrorCode.ACCESS_DENIED);
        }

        auditAllowed(principal, id, targetSubjectId.value(), WorkspaceAuthorizationAction.MEMBER_REMOVE);
        if (!memberRepository.deleteNonOwner(id, targetSubjectId)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
    }

    @Transactional
    public WorkspaceMember transferOwnership(
            AuthenticatedPrincipal principal, UUID id, TransferWorkspaceOwnershipCommand command) {
        principal = requireHuman(principal);
        Objects.requireNonNull(command, "command");
        SubjectId targetSubjectId = subjectId(command.newOwnerSubjectId());
        requireScope(principal, WorkspaceAuthorities.WRITE,
                WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER, id, targetSubjectId.value());
        Workspace workspace = requireWorkspace(
                principal, id, WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                targetSubjectId.value(), true);
        SubjectId actorSubjectId = subjectId(principal.subjectId());
        AuthenticatedPrincipal authenticatedPrincipal = principal;
        WorkspaceMember currentOwner = memberRepository.findByWorkspaceAndSubject(
                        id, actorSubjectId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> denied(authenticatedPrincipal, id, targetSubjectId.value(),
                        WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                        WorkspaceErrorCode.NOT_FOUND));
        if (currentOwner.role() != WorkspaceRole.OWNER) {
            throw denied(principal, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                    WorkspaceErrorCode.ACCESS_DENIED);
        }
        if (actorSubjectId.equals(targetSubjectId)) {
            auditAllowed(principal, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
            return currentOwner;
        }
        WorkspaceMember target = requireTargetMember(
                principal, id, targetSubjectId, WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
        if (!target.isActive()) {
            throw denied(principal, id, targetSubjectId.value(),
                    WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER,
                    WorkspaceErrorCode.MEMBER_NOT_ACTIVE);
        }

        Instant now = clock.instant();
        auditAllowed(principal, id, targetSubjectId.value(),
                WorkspaceAuthorizationAction.OWNERSHIP_TRANSFER);
        if (!memberRepository.demoteOwner(id, actorSubjectId, now)
                || !memberRepository.promoteActiveMemberToOwner(
                        id, targetSubjectId, target.role(), now)) {
            throw new BusinessException(WorkspaceErrorCode.MEMBER_UPDATE_CONFLICT);
        }
        return new WorkspaceMember(
                workspace.id(), target.subjectId(), WorkspaceRole.OWNER,
                target.status(), target.invitedBy(), target.createdAt(), target.activatedAt(), now);
    }

    @Transactional(readOnly = true)
    public WorkspaceAuthorizationAuditPage authorizationAudits(
            AuthenticatedPrincipal principal, UUID id, int page, int size) {
        principal = requireHuman(principal);
        requireScope(principal, WorkspaceAuthorities.AUDIT_READ,
                WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, id, null);
        WorkspaceAccess access = requireAccess(
                principal, id, WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, null);
        requireManager(principal, access.member(), id,
                WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ, null);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(WorkspaceErrorCode.INVALID_PAGE);
        }
        long offset = Math.multiplyExact((long) page - 1, size);
        auditAllowed(principal, id, null, WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ);
        return auditService.findPage(id, page, size, offset);
    }

    private WorkspaceAccess requireAccess(
            AuthenticatedPrincipal principal,
            UUID id,
            WorkspaceAuthorizationAction action,
            String targetSubjectId) {
        Objects.requireNonNull(id, "id");
        SubjectId subjectId = subjectId(principal.subjectId());
        Workspace workspace = requireWorkspace(principal, id, action, targetSubjectId, false);
        WorkspaceMember member = memberRepository.findByWorkspaceAndSubject(id, subjectId)
                .filter(WorkspaceMember::isActive)
                .orElseThrow(() -> denied(
                        principal, id, targetSubjectId, action, WorkspaceErrorCode.NOT_FOUND));
        return new WorkspaceAccess(workspace, member);
    }

    private Workspace requireWorkspace(
            AuthenticatedPrincipal principal,
            UUID id,
            WorkspaceAuthorizationAction action,
            String targetSubjectId,
            boolean forUpdate) {
        return (forUpdate
                ? workspaceRepository.findByIdForUpdate(id)
                : workspaceRepository.findById(id))
                .orElseThrow(() -> denied(
                        principal, id, targetSubjectId, action, WorkspaceErrorCode.NOT_FOUND));
    }

    private WorkspaceMember requireTargetMember(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            SubjectId targetSubjectId,
            WorkspaceAuthorizationAction action) {
        return memberRepository.findByWorkspaceAndSubject(workspaceId, targetSubjectId)
                .orElseThrow(() -> denied(
                        principal, workspaceId, targetSubjectId.value(), action, WorkspaceErrorCode.NOT_FOUND));
    }

    private void requireManager(
            AuthenticatedPrincipal principal,
            WorkspaceMember member,
            UUID workspaceId,
            WorkspaceAuthorizationAction action,
            String targetSubjectId) {
        if (!member.role().canManageWorkspace()) {
            throw denied(principal, workspaceId, targetSubjectId, action, WorkspaceErrorCode.ACCESS_DENIED);
        }
    }

    private void requireScope(
            AuthenticatedPrincipal principal,
            String authority,
            WorkspaceAuthorizationAction action,
            UUID workspaceId,
            String targetSubjectId) {
        String scope = authority.startsWith("SCOPE_") ? authority.substring("SCOPE_".length()) : authority;
        if (!principal.scopes().contains(scope)) {
            throw denied(principal, workspaceId, targetSubjectId, action, StandardErrorCode.FORBIDDEN);
        }
    }

    private BusinessException denied(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action,
            ErrorCode reason) {
        auditService.record(
                principal, workspaceId, targetSubjectId, action,
                WorkspaceAuthorizationDecision.DENIED, reason);
        return new BusinessException(reason);
    }

    private void auditAllowed(
            AuthenticatedPrincipal principal,
            UUID workspaceId,
            String targetSubjectId,
            WorkspaceAuthorizationAction action) {
        auditService.record(
                principal, workspaceId, targetSubjectId, action,
                WorkspaceAuthorizationDecision.ALLOWED, StandardErrorCode.OK);
    }

    private AuthenticatedPrincipal requireHuman(AuthenticatedPrincipal principal) {
        if (principal == null || !principal.isHuman()) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return principal;
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

    private String principalSubject(AuthenticatedPrincipal principal) {
        return principal.subjectId();
    }

    private record WorkspaceAccess(Workspace workspace, WorkspaceMember member) {
    }
}
