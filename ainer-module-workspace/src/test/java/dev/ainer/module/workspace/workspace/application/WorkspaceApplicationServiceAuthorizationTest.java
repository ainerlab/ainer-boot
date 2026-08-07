package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceApplicationServiceAuthorizationTest {

    private static final IdentityAuthorityRef AUTHORITY = new IdentityAuthorityRef("https://auth.ainer.test");
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private InMemoryMemberRepository members;
    private InMemoryAuditRepository audits;
    private WorkspaceApplicationService service;

    @BeforeEach
    void setUp() {
        members = new InMemoryMemberRepository();
        audits = new InMemoryAuditRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new WorkspaceApplicationService(
                new InMemoryWorkspaceRepository(members),
                members,
                new WorkspaceAuthorizationAuditService(audits, clock),
                clock);
    }

    @Test
    void createDerivesActiveOwnerAndAuditsDecision() {
        AuthenticatedPrincipal owner = human("account:owner", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);

        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("研发空间"));

        assertThat(workspace.id()).isNotNull();
        assertThat(members.findByWorkspaceAndSubject(workspace.id(), new SubjectId("account:owner")))
                .get().extracting(WorkspaceMember::role).isEqualTo(WorkspaceRole.OWNER);
        assertThat(audits.items).anySatisfy(audit -> {
            assertThat(audit.action()).isEqualTo(WorkspaceAuthorizationAction.WORKSPACE_CREATE);
            assertThat(audit.decision()).isEqualTo(WorkspaceAuthorizationDecision.ALLOWED);
        });
    }

    @Test
    void invitationRequiresActiveHumanAccountAndAcceptanceBeforeAccess() {
        AuthenticatedPrincipal owner = human("account:owner", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        AuthenticatedPrincipal member = human("account:member", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("邀请空间"));

        WorkspaceMember invitation = service.addMember(
                owner, workspace.id(), new AddWorkspaceMemberCommand("account:member", WorkspaceRole.MEMBER));
        assertThat(invitation.status()).isEqualTo(WorkspaceMemberStatus.PENDING);
        assertError(() -> service.get(member, workspace.id()), WorkspaceErrorCode.NOT_FOUND);

        assertThat(service.acceptInvitation(member, workspace.id()).status())
                .isEqualTo(WorkspaceMemberStatus.ACTIVE);
        assertThat(service.get(member, workspace.id()).id()).isEqualTo(workspace.id());
    }

    @Test
    void membershipPreventsCrossWorkspaceAccess() {
        AuthenticatedPrincipal first = human("account:first", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        AuthenticatedPrincipal second = human("account:second", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        Workspace firstWorkspace = service.create(first, new CreateWorkspaceCommand("第一空间"));
        Workspace secondWorkspace = service.create(second, new CreateWorkspaceCommand("第二空间"));

        assertThat(service.get(first, firstWorkspace.id()).id()).isEqualTo(firstWorkspace.id());
        assertError(() -> service.get(first, secondWorkspace.id()), WorkspaceErrorCode.NOT_FOUND);
    }

    @Test
    void servicePrincipalCannotBecomeWorkspaceMember() {
        AuthenticatedPrincipal servicePrincipal = new AuthenticatedPrincipal(
                new ServiceSubjectRef(AUTHORITY, "service:one"), AUTHORITY,
                TokenProfile.SERVICE_V1, "1", Set.of("ainer-api"),
                Set.of("workspace.write"), "client_credentials", "client-1", 0L);

        assertError(() -> service.create(servicePrincipal, new CreateWorkspaceCommand("服务空间")),
                StandardErrorCode.FORBIDDEN);
    }

    @Test
    void ownerCanTransferOwnershipButCannotRemoveOwnerThroughGenericEndpoint() {
        AuthenticatedPrincipal owner = human("account:owner", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        AuthenticatedPrincipal target = human("account:target", WorkspaceAuthorities.READ, WorkspaceAuthorities.WRITE);
        Workspace workspace = service.create(owner, new CreateWorkspaceCommand("治理空间"));
        service.addMember(owner, workspace.id(), new AddWorkspaceMemberCommand("account:target", WorkspaceRole.ADMIN));
        service.acceptInvitation(target, workspace.id());

        assertThat(service.transferOwnership(
                owner, workspace.id(), new TransferWorkspaceOwnershipCommand("account:target")).role())
                .isEqualTo(WorkspaceRole.OWNER);
        assertError(() -> service.removeMember(
                target, workspace.id(), new RemoveWorkspaceMemberCommand("account:target")),
                WorkspaceErrorCode.ACCESS_DENIED);
    }

    private static AuthenticatedPrincipal human(String subjectId, String... scopes) {
        return new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, subjectId), AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1, "1", Set.of("ainer-api"),
                Arrays.stream(scopes)
                        .map(scope -> scope.startsWith("SCOPE_")
                                ? scope.substring("SCOPE_".length()) : scope)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                "pwd", null, 0L);
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(() -> action.run())
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<UUID, Workspace> items = new HashMap<>();
        private final InMemoryMemberRepository members;

        private InMemoryWorkspaceRepository(InMemoryMemberRepository members) {
            this.members = members;
        }

        @Override public void insert(Workspace workspace) { items.put(workspace.id(), workspace); }
        @Override public boolean update(Workspace workspace, long expectedVersion) {
            items.put(workspace.id(), workspace);
            return true;
        }
        @Override public Optional<Workspace> findById(UUID id) { return Optional.ofNullable(items.get(id)); }
        @Override public Optional<Workspace> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public WorkspacePage findPage(SubjectId subjectId, int page, int size, long offset) {
            List<Workspace> visible = items.values().stream()
                    .filter(workspace -> members.findByWorkspaceAndSubject(workspace.id(), subjectId)
                            .filter(WorkspaceMember::isActive).isPresent())
                    .toList();
            return new WorkspacePage(visible, page, size, visible.size());
        }
    }

    private static final class InMemoryMemberRepository implements WorkspaceMemberRepository {
        private final Map<String, WorkspaceMember> items = new HashMap<>();
        private static String key(UUID workspaceId, SubjectId subjectId) {
            return workspaceId + "/" + subjectId.value();
        }
        @Override public void insert(WorkspaceMember member) { items.put(key(member.workspaceId(), member.subjectId()), member); }
        @Override public Optional<WorkspaceMember> findByWorkspaceAndSubject(UUID workspaceId, SubjectId subjectId) {
            return Optional.ofNullable(items.get(key(workspaceId, subjectId)));
        }
        @Override public boolean activatePending(UUID workspaceId, SubjectId subjectId, Instant activatedAt) {
            WorkspaceMember current = findByWorkspaceAndSubject(workspaceId, subjectId).orElse(null);
            if (current == null || current.status() != WorkspaceMemberStatus.PENDING) return false;
            insert(new WorkspaceMember(current.workspaceId(), current.subjectId(), current.role(),
                    WorkspaceMemberStatus.ACTIVE, current.invitedBy(), current.createdAt(), activatedAt, activatedAt));
            return true;
        }
        @Override public boolean updateRole(UUID workspaceId, SubjectId subjectId, WorkspaceRole expectedRole,
                                            WorkspaceRole newRole, Instant updatedAt) {
            WorkspaceMember current = findByWorkspaceAndSubject(workspaceId, subjectId).orElse(null);
            if (current == null || current.role() != expectedRole) return false;
            insert(new WorkspaceMember(current.workspaceId(), current.subjectId(), newRole, current.status(),
                    current.invitedBy(), current.createdAt(), current.activatedAt(), updatedAt));
            return true;
        }
        @Override public boolean deleteNonOwner(UUID workspaceId, SubjectId subjectId) {
            WorkspaceMember current = findByWorkspaceAndSubject(workspaceId, subjectId).orElse(null);
            return current != null && current.role() != WorkspaceRole.OWNER && items.remove(key(workspaceId, subjectId)) != null;
        }
        @Override public boolean demoteOwner(UUID workspaceId, SubjectId subjectId, Instant updatedAt) {
            return updateRole(workspaceId, subjectId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN, updatedAt);
        }
        @Override public boolean promoteActiveMemberToOwner(UUID workspaceId, SubjectId subjectId,
                                                             WorkspaceRole expectedRole, Instant updatedAt) {
            WorkspaceMember current = findByWorkspaceAndSubject(workspaceId, subjectId).orElse(null);
            if (current == null || !current.isActive() || current.role() != expectedRole) return false;
            return updateRole(workspaceId, subjectId, expectedRole, WorkspaceRole.OWNER, updatedAt);
        }
        @Override public boolean hasActiveOwner(UUID workspaceId) {
            return items.values().stream().anyMatch(m -> m.workspaceId().equals(workspaceId)
                    && m.role() == WorkspaceRole.OWNER && m.status() == WorkspaceMemberStatus.ACTIVE);
        }
        @Override public boolean hasRevokedOwner(UUID workspaceId) {
            return items.values().stream().anyMatch(m -> m.workspaceId().equals(workspaceId)
                    && m.role() == WorkspaceRole.OWNER && m.status() == WorkspaceMemberStatus.REVOKED);
        }
    }

    private static final class InMemoryAuditRepository implements WorkspaceAuthorizationAuditRepository {
        private final List<WorkspaceAuthorizationAudit> items = new ArrayList<>();
        @Override public void insert(WorkspaceAuthorizationAudit audit) { items.add(audit); }
        @Override public WorkspaceAuthorizationAuditPage findPage(UUID workspaceId, int page, int size, long offset) {
            List<WorkspaceAuthorizationAudit> result = items.stream()
                    .filter(audit -> workspaceId.equals(audit.workspaceId())).toList();
            return new WorkspaceAuthorizationAuditPage(result, page, size, result.size());
        }
        @Override public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) { return 0; }
        @Override public List<WorkspaceAuthorizationAudit> exportAfter(UUID workspaceId,
                WorkspaceAuthorizationAuditCursor cursor, int limit) { return List.of(); }
        @Override public WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince) {
            return new WorkspaceAuthorizationAuditOperationalStatus(0, 0, 0, 0, null);
        }
    }
}
