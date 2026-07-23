package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.security.actor.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceApplicationServiceAuthorizationTest {

    private static final String TENANT = "tenant:test";
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");
    private static final AuthenticatedActor OWNER = actor(
            "subject:owner", TENANT,
            WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE, WorkspaceAuthorities.AUDIT_READ);

    private InMemoryMemberRepository memberRepository;
    private InMemoryAuditRepository auditRepository;
    private WorkspaceApplicationService service;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        auditRepository = new InMemoryAuditRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new WorkspaceApplicationService(
                new InMemoryWorkspaceRepository(memberRepository),
                memberRepository,
                new WorkspaceAuthorizationAuditService(auditRepository, clock),
                java.util.Optional.empty(),
                clock);
    }

    @Test
    void createDerivesActiveOwnerAndAuditsDecision() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("研发空间"));

        WorkspaceMember owner = member(workspace, OWNER.subjectId()).orElseThrow();
        assertThat(workspace.tenantId().value()).isEqualTo(TENANT);
        assertThat(owner.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(owner.status()).isEqualTo(WorkspaceMemberStatus.ACTIVE);
        assertThat(auditRepository.audits).anySatisfy(audit -> {
            assertThat(audit.action()).isEqualTo(WorkspaceAuthorizationAction.WORKSPACE_CREATE);
            assertThat(audit.decision()).isEqualTo(WorkspaceAuthorizationDecision.ALLOWED);
        });
    }

    @Test
    void invitationDoesNotGrantAccessUntilTrustedSubjectAcceptsIt() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("邀请空间"));
        WorkspaceMember invitation = service.addMember(
                OWNER, workspace.id(),
                new AddWorkspaceMemberCommand("subject:member", WorkspaceRole.MEMBER));
        AuthenticatedActor member = actor(
                "subject:member", TENANT, WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);

        assertThat(invitation.status()).isEqualTo(WorkspaceMemberStatus.PENDING);
        assertError(() -> service.get(member, workspace.id()), WorkspaceErrorCode.NOT_FOUND);

        WorkspaceMember accepted = service.acceptInvitation(member, workspace.id());

        assertThat(accepted.status()).isEqualTo(WorkspaceMemberStatus.ACTIVE);
        assertThat(service.get(member, workspace.id()).id()).isEqualTo(workspace.id());
    }

    @Test
    void enabledIdentityDirectoryRejectsUnknownInvitationTarget() {
        InMemoryMemberRepository members = new InMemoryMemberRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        WorkspaceApplicationService directoryProtectedService = new WorkspaceApplicationService(
                new InMemoryWorkspaceRepository(members),
                members,
                new WorkspaceAuthorizationAuditService(new InMemoryAuditRepository(), clock),
                Optional.of((tenantId, subjectId) -> false),
                clock);
        Workspace workspace = directoryProtectedService.create(
                OWNER, new CreateWorkspaceCommand("目录保护空间"));

        assertError(() -> directoryProtectedService.addMember(
                        OWNER,
                        workspace.id(),
                        new AddWorkspaceMemberCommand("subject:unknown", WorkspaceRole.MEMBER)),
                WorkspaceErrorCode.IDENTITY_DIRECTORY_MEMBER_NOT_FOUND);
        assertThat(members.findByWorkspaceAndSubject(
                workspace.tenantId(), workspace.id(), new SubjectId("subject:unknown"))).isEmpty();
    }

    @Test
    void readRequiresScopeActiveMembershipAndTrustedTenant() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("隔离空间"));

        assertError(
                () -> service.get(actor("subject:owner", TENANT), workspace.id()),
                StandardErrorCode.FORBIDDEN);
        assertError(
                () -> service.get(actor(
                        "subject:owner", "tenant:other", WorkspaceAuthorities.READ), workspace.id()),
                WorkspaceErrorCode.NOT_FOUND);
        assertError(
                () -> service.get(actor(
                        "subject:outsider", TENANT, WorkspaceAuthorities.READ), workspace.id()),
                WorkspaceErrorCode.NOT_FOUND);

        assertThat(auditRepository.audits)
                .filteredOn(audit -> audit.decision() == WorkspaceAuthorizationDecision.DENIED)
                .extracting(WorkspaceAuthorizationAudit::reasonCode)
                .contains(
                        StandardErrorCode.FORBIDDEN.code(),
                        WorkspaceErrorCode.NOT_FOUND.code());
    }

    @Test
    void ownerCanChangeAndRemoveNonOwnerMember() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("成员空间"));
        AuthenticatedActor memberActor = actor(
                "subject:member", TENANT, WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(memberActor.subjectId(), WorkspaceRole.ADMIN));
        service.acceptInvitation(memberActor, workspace.id());

        WorkspaceMember changed = service.changeMemberRole(
                OWNER, workspace.id(),
                new ChangeWorkspaceMemberRoleCommand(memberActor.subjectId(), WorkspaceRole.MEMBER));
        service.removeMember(
                OWNER, workspace.id(), new RemoveWorkspaceMemberCommand(memberActor.subjectId()));

        assertThat(changed.role()).isEqualTo(WorkspaceRole.MEMBER);
        assertError(() -> service.get(memberActor, workspace.id()), WorkspaceErrorCode.NOT_FOUND);
    }

    @Test
    void memberCannotManageAndOwnerCannotUseGenericMutation() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("受控空间"));
        AuthenticatedActor memberActor = actor(
                "subject:member", TENANT, WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(memberActor.subjectId(), WorkspaceRole.MEMBER));
        service.acceptInvitation(memberActor, workspace.id());

        assertError(
                () -> service.rename(memberActor, workspace.id(), "成员越权改名"),
                WorkspaceErrorCode.ACCESS_DENIED);
        assertError(
                () -> service.changeMemberRole(
                        OWNER, workspace.id(),
                        new ChangeWorkspaceMemberRoleCommand(OWNER.subjectId(), WorkspaceRole.ADMIN)),
                WorkspaceErrorCode.ACCESS_DENIED);
        assertError(
                () -> service.removeMember(
                        OWNER, workspace.id(), new RemoveWorkspaceMemberCommand(OWNER.subjectId())),
                WorkspaceErrorCode.ACCESS_DENIED);
        assertError(
                () -> service.addMember(
                        OWNER, workspace.id(),
                        new AddWorkspaceMemberCommand("subject:next-owner", WorkspaceRole.OWNER)),
                WorkspaceErrorCode.ROLE_NOT_ASSIGNABLE);
    }

    @Test
    void ownershipTransferLeavesExactlyOneActiveOwner() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("所有权空间"));
        AuthenticatedActor nextOwner = actor(
                "subject:next-owner", TENANT, WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(nextOwner.subjectId(), WorkspaceRole.ADMIN));
        service.acceptInvitation(nextOwner, workspace.id());

        WorkspaceMember transferred = service.transferOwnership(
                OWNER, workspace.id(), new TransferWorkspaceOwnershipCommand(nextOwner.subjectId()));

        assertThat(transferred.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(member(workspace, OWNER.subjectId()).orElseThrow().role())
                .isEqualTo(WorkspaceRole.ADMIN);
        assertThat(memberRepository.members.values())
                .filteredOn(member -> member.workspaceId().equals(workspace.id()))
                .filteredOn(member -> member.role() == WorkspaceRole.OWNER && member.isActive())
                .hasSize(1);
        assertError(
                () -> service.transferOwnership(
                        OWNER, workspace.id(), new TransferWorkspaceOwnershipCommand(nextOwner.subjectId())),
                WorkspaceErrorCode.ACCESS_DENIED);
    }

    @Test
    void pageIncludesOnlyActiveMembershipsInCurrentTenant() {
        Workspace owned = service.create(OWNER, new CreateWorkspaceCommand("我的空间"));
        AuthenticatedActor pendingActor = actor(
                "subject:pending", TENANT, WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        service.addMember(OWNER, owned.id(),
                new AddWorkspaceMemberCommand(pendingActor.subjectId(), WorkspaceRole.MEMBER));

        assertThat(service.page(pendingActor, 1, 20).total()).isZero();
        service.acceptInvitation(pendingActor, owned.id());
        assertThat(service.page(pendingActor, 1, 20).items())
                .extracting(Workspace::id)
                .containsExactly(owned.id());
    }

    @Test
    void onlyManagerWithAuditScopeCanReadTenantBoundAuthorizationAudit() {
        Workspace workspace = service.create(OWNER, new CreateWorkspaceCommand("审计空间"));
        service.rename(OWNER, workspace.id(), "审计交付空间");

        WorkspaceAuthorizationAuditPage page = service.authorizationAudits(
                OWNER, workspace.id(), 1, 20);

        assertThat(page.items())
                .extracting(WorkspaceAuthorizationAudit::action)
                .contains(
                        WorkspaceAuthorizationAction.AUTHORIZATION_AUDIT_READ,
                        WorkspaceAuthorizationAction.WORKSPACE_RENAME,
                        WorkspaceAuthorizationAction.WORKSPACE_CREATE);

        AuthenticatedActor memberActor = actor(
                "subject:audit-member", TENANT,
                WorkspaceAuthorities.READ, WorkspaceAuthorities.AUDIT_READ);
        service.addMember(OWNER, workspace.id(),
                new AddWorkspaceMemberCommand(memberActor.subjectId(), WorkspaceRole.MEMBER));
        service.acceptInvitation(memberActor, workspace.id());
        assertError(
                () -> service.authorizationAudits(memberActor, workspace.id(), 1, 20),
                WorkspaceErrorCode.ACCESS_DENIED);
        assertError(
                () -> service.authorizationAudits(
                        actor("subject:owner", TENANT, WorkspaceAuthorities.READ),
                        workspace.id(), 1, 20),
                StandardErrorCode.FORBIDDEN);
    }

    private Optional<WorkspaceMember> member(Workspace workspace, String subjectId) {
        return memberRepository.findByWorkspaceAndSubject(
                workspace.tenantId(), workspace.id(), new SubjectId(subjectId));
    }

    private static void assertError(Runnable invocation, ErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static AuthenticatedActor actor(String subjectId, String tenantId, String... authorities) {
        return new AuthenticatedActor(subjectId, tenantId, Set.of(authorities));
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {

        private final Map<UUID, Workspace> workspaces = new LinkedHashMap<>();
        private final InMemoryMemberRepository members;

        private InMemoryWorkspaceRepository(InMemoryMemberRepository members) {
            this.members = members;
        }

        @Override
        public void insert(Workspace workspace) {
            if (workspaces.putIfAbsent(workspace.id(), workspace) != null) {
                throw new BusinessException(WorkspaceErrorCode.ALREADY_EXISTS);
            }
        }

        @Override
        public boolean update(Workspace workspace, long expectedVersion) {
            Workspace current = workspaces.get(workspace.id());
            if (current == null
                    || !current.tenantId().equals(workspace.tenantId())
                    || current.version() != expectedVersion) {
                return false;
            }
            workspaces.put(workspace.id(), workspace);
            return true;
        }

        @Override
        public Optional<Workspace> findById(TenantId tenantId, UUID id) {
            return Optional.ofNullable(workspaces.get(id))
                    .filter(workspace -> workspace.tenantId().equals(tenantId));
        }

        @Override
        public Optional<Workspace> findByIdForUpdate(TenantId tenantId, UUID id) {
            return findById(tenantId, id);
        }

        @Override
        public WorkspacePage findPage(
                TenantId tenantId, SubjectId subjectId, int page, int size, long offset) {
            List<Workspace> accessible = workspaces.values().stream()
                    .filter(workspace -> workspace.tenantId().equals(tenantId))
                    .filter(workspace -> members.isActive(tenantId, workspace.id(), subjectId))
                    .sorted(Comparator.comparing(Workspace::createdAt).reversed()
                            .thenComparing(Workspace::id).reversed())
                    .toList();
            int from = Math.min(Math.toIntExact(offset), accessible.size());
            int to = Math.min(from + size, accessible.size());
            return new WorkspacePage(new ArrayList<>(accessible.subList(from, to)), page, size, accessible.size());
        }
    }

    private static final class InMemoryMemberRepository implements WorkspaceMemberRepository {

        private final Map<MemberKey, WorkspaceMember> members = new LinkedHashMap<>();

        @Override
        public void insert(WorkspaceMember member) {
            MemberKey key = key(member.tenantId(), member.workspaceId(), member.subjectId());
            if (members.putIfAbsent(key, member) != null) {
                throw new BusinessException(WorkspaceErrorCode.MEMBER_ALREADY_EXISTS);
            }
        }

        @Override
        public Optional<WorkspaceMember> findByWorkspaceAndSubject(
                TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return Optional.ofNullable(members.get(key(tenantId, workspaceId, subjectId)));
        }

        @Override
        public boolean activatePending(
                TenantId tenantId, UUID workspaceId, SubjectId subjectId, Instant activatedAt) {
            MemberKey key = key(tenantId, workspaceId, subjectId);
            WorkspaceMember current = members.get(key);
            if (current == null || current.status() != WorkspaceMemberStatus.PENDING) {
                return false;
            }
            members.put(key, copy(current, current.role(), WorkspaceMemberStatus.ACTIVE, activatedAt, activatedAt));
            return true;
        }

        @Override
        public boolean updateRole(
                TenantId tenantId,
                UUID workspaceId,
                SubjectId subjectId,
                WorkspaceRole expectedRole,
                WorkspaceRole newRole,
                Instant updatedAt) {
            MemberKey key = key(tenantId, workspaceId, subjectId);
            WorkspaceMember current = members.get(key);
            if (current == null || current.role() != expectedRole || current.role() == WorkspaceRole.OWNER) {
                return false;
            }
            members.put(key, copy(current, newRole, current.status(), current.activatedAt(), updatedAt));
            return true;
        }

        @Override
        public boolean deleteNonOwner(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            MemberKey key = key(tenantId, workspaceId, subjectId);
            WorkspaceMember current = members.get(key);
            if (current == null || current.role() == WorkspaceRole.OWNER) {
                return false;
            }
            members.remove(key);
            return true;
        }

        @Override
        public boolean demoteOwner(
                TenantId tenantId, UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt) {
            MemberKey key = key(tenantId, workspaceId, ownerSubjectId);
            WorkspaceMember current = members.get(key);
            if (current == null || current.role() != WorkspaceRole.OWNER || !current.isActive()) {
                return false;
            }
            members.put(key, copy(current, WorkspaceRole.ADMIN, current.status(), current.activatedAt(), updatedAt));
            return true;
        }

        @Override
        public boolean promoteActiveMemberToOwner(
                TenantId tenantId,
                UUID workspaceId,
                SubjectId subjectId,
                WorkspaceRole expectedRole,
                Instant updatedAt) {
            MemberKey key = key(tenantId, workspaceId, subjectId);
            WorkspaceMember current = members.get(key);
            if (current == null || current.role() != expectedRole || !current.isActive()) {
                return false;
            }
            members.put(key, copy(current, WorkspaceRole.OWNER, current.status(), current.activatedAt(), updatedAt));
            return true;
        }

        @Override
        public boolean hasActiveOwner(TenantId tenantId, UUID workspaceId) {
            return members.values().stream().anyMatch(member ->
                    member.tenantId().equals(tenantId)
                            && member.workspaceId().equals(workspaceId)
                            && member.role() == WorkspaceRole.OWNER
                            && member.status() == WorkspaceMemberStatus.ACTIVE);
        }

        @Override
        public boolean hasRevokedOwner(TenantId tenantId, UUID workspaceId) {
            return members.values().stream().anyMatch(member ->
                    member.tenantId().equals(tenantId)
                            && member.workspaceId().equals(workspaceId)
                            && member.role() == WorkspaceRole.OWNER
                            && member.status() == WorkspaceMemberStatus.REVOKED);
        }

        private boolean isActive(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return findByWorkspaceAndSubject(tenantId, workspaceId, subjectId)
                    .filter(WorkspaceMember::isActive)
                    .isPresent();
        }

        private static MemberKey key(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return new MemberKey(tenantId, workspaceId, subjectId);
        }

        private static WorkspaceMember copy(
                WorkspaceMember current,
                WorkspaceRole role,
                WorkspaceMemberStatus status,
                Instant activatedAt,
                Instant updatedAt) {
            return new WorkspaceMember(
                    current.tenantId(), current.workspaceId(), current.subjectId(), role, status,
                    current.invitedBy(), current.createdAt(), activatedAt, updatedAt);
        }
    }

    private static final class InMemoryAuditRepository
            implements WorkspaceAuthorizationAuditRepository {

        private final List<WorkspaceAuthorizationAudit> audits = new ArrayList<>();

        @Override
        public void insert(WorkspaceAuthorizationAudit audit) {
            audits.add(audit);
        }

        @Override
        public WorkspaceAuthorizationAuditPage findPage(
                TenantId tenantId, UUID workspaceId, int page, int size, long offset) {
            List<WorkspaceAuthorizationAudit> accessible = audits.stream()
                    .filter(audit -> audit.tenantId().equals(tenantId.value()))
                    .filter(audit -> workspaceId.equals(audit.workspaceId()))
                    .sorted(Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt).reversed()
                            .thenComparing(WorkspaceAuthorizationAudit::id).reversed())
                    .toList();
            int from = Math.min(Math.toIntExact(offset), accessible.size());
            int to = Math.min(from + size, accessible.size());
            return new WorkspaceAuthorizationAuditPage(
                    accessible.subList(from, to), page, size, accessible.size());
        }

        @Override
        public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) {
            return 0;
        }

        @Override
        public List<WorkspaceAuthorizationAudit> exportAfter(
                TenantId tenantId, WorkspaceAuthorizationAuditCursor cursor, int limit) {
            return audits.stream()
                    .filter(audit -> audit.tenantId().equals(tenantId.value()))
                    .filter(audit -> cursor == null
                            || audit.occurredAt().isAfter(cursor.occurredAt())
                            || (audit.occurredAt().equals(cursor.occurredAt())
                            && audit.id().compareTo(cursor.id()) > 0))
                    .sorted(Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt)
                            .thenComparing(WorkspaceAuthorizationAudit::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince) {
            long denied = audits.stream()
                    .filter(audit -> audit.decision() == WorkspaceAuthorizationDecision.DENIED)
                    .filter(audit -> !audit.occurredAt().isBefore(deniedSince))
                    .count();
            return new WorkspaceAuthorizationAuditOperationalStatus(
                    audits.size(), 0, denied, 0,
                    audits.stream().map(WorkspaceAuthorizationAudit::occurredAt).min(Instant::compareTo).orElse(null));
        }
    }

    private record MemberKey(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
    }
}
