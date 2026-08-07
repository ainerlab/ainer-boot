package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.Workspace;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.WorkspaceMemberStatus;
import dev.ainer.module.workspace.workspace.domain.WorkspaceName;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceSecurityOperationsTest {

    private static final Instant NOW = Instant.parse("2026-08-07T05:00:00Z");

    @Test
    void ownerRecoveryUsesWorkspaceBoundaryAndSeparationOfDuties() {
        UUID workspaceId = UUID.randomUUID();
        SubjectId revokedOwner = new SubjectId("account:revoked-owner");
        SubjectId target = new SubjectId("account:active-admin");
        InMemoryWorkspaceRepository workspaces = new InMemoryWorkspaceRepository(
                Workspace.create(workspaceId, new WorkspaceName("恢复空间"), NOW.minusSeconds(3600)));
        InMemoryMemberRepository members = new InMemoryMemberRepository(
                member(workspaceId, revokedOwner, WorkspaceRole.OWNER, WorkspaceMemberStatus.REVOKED),
                member(workspaceId, target, WorkspaceRole.ADMIN, WorkspaceMemberStatus.ACTIVE));
        InMemoryOwnerRecoveryRepository recoveries = new InMemoryOwnerRecoveryRepository();
        InMemorySecurityAuditRepository audits = new InMemorySecurityAuditRepository();
        WorkspaceOwnerRecoveryService service = new WorkspaceOwnerRecoveryService(
                workspaces, members, recoveries, audits, Clock.fixed(NOW, ZoneOffset.UTC));

        WorkspaceOwnerRecoveryRequest request = service.requestRecovery(
                "operator:request", workspaceId, target, "INC-OWNER-42", Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.approveAndExecute("operator:request", request.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_APPROVER_MUST_DIFFER));
        WorkspaceOwnerRecoveryRequest executed = service.approveAndExecute("operator:approve", request.id());

        assertThat(executed.status()).isEqualTo("EXECUTED");
        assertThat(members.findByWorkspaceAndSubject(workspaceId, target).orElseThrow().role())
                .isEqualTo(WorkspaceRole.OWNER);
        assertThat(members.findByWorkspaceAndSubject(workspaceId, revokedOwner).orElseThrow().status())
                .isEqualTo(WorkspaceMemberStatus.REVOKED);
        assertThat(audits.items).extracting(WorkspaceSecurityOperationAudit::phase)
                .containsExactly("REQUESTED", "EXECUTED");
    }

    @Test
    void auditExportUsesWorkspaceCursorAndRecordsAccess() {
        InMemoryAuthorizationAuditRepository repository = new InMemoryAuthorizationAuditRepository();
        InMemorySecurityAuditRepository operations = new InMemorySecurityAuditRepository();
        UUID workspaceId = UUID.randomUUID();
        repository.items.add(audit("00000000-0000-0000-0000-000000000001", workspaceId, NOW.minusSeconds(30)));
        repository.items.add(audit("00000000-0000-0000-0000-000000000002", workspaceId, NOW.minusSeconds(20)));
        repository.items.add(audit("00000000-0000-0000-0000-000000000003", workspaceId, NOW.minusSeconds(10)));
        WorkspaceAuthorizationAuditLifecycleService service = new WorkspaceAuthorizationAuditLifecycleService(
                repository, operations, Clock.fixed(NOW, ZoneOffset.UTC));

        WorkspaceAuthorizationAuditExportBatch first = service.export("siem:exporter", workspaceId, null, 2);
        WorkspaceAuthorizationAuditExportBatch second = service.export(
                "siem:exporter", workspaceId, first.nextCursor(), 2);

        assertThat(first.items()).extracting(WorkspaceAuthorizationAudit::id)
                .containsExactly(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(second.items()).extracting(WorkspaceAuthorizationAudit::id)
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assertThat(operations.items).extracting(WorkspaceSecurityOperationAudit::recordCount)
                .containsExactly(2, 1);
    }

    private static WorkspaceMember member(
            UUID workspaceId, SubjectId subjectId, WorkspaceRole role, WorkspaceMemberStatus status) {
        Instant activatedAt = status == WorkspaceMemberStatus.PENDING ? null : NOW.minusSeconds(1800);
        return new WorkspaceMember(
                workspaceId, subjectId, role, status, subjectId,
                NOW.minusSeconds(1800), activatedAt, NOW.minusSeconds(60));
    }

    private static WorkspaceAuthorizationAudit audit(String id, UUID workspaceId, Instant occurredAt) {
        return new WorkspaceAuthorizationAudit(
                UUID.fromString(id), workspaceId, "account:actor", null,
                WorkspaceAuthorizationAction.WORKSPACE_READ,
                WorkspaceAuthorizationDecision.ALLOWED, "AINER.COMMON.OK", occurredAt);
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<UUID, Workspace> items = new HashMap<>();
        private InMemoryWorkspaceRepository(Workspace workspace) { items.put(workspace.id(), workspace); }
        @Override public void insert(Workspace workspace) { items.put(workspace.id(), workspace); }
        @Override public boolean update(Workspace workspace, long expectedVersion) { items.put(workspace.id(), workspace); return true; }
        @Override public Optional<Workspace> findById(UUID id) { return Optional.ofNullable(items.get(id)); }
        @Override public Optional<Workspace> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public WorkspacePage findPage(SubjectId subjectId, int page, int size, long offset) {
            return new WorkspacePage(List.of(), page, size, 0);
        }
    }

    private static final class InMemoryMemberRepository implements WorkspaceMemberRepository {
        private final Map<String, WorkspaceMember> items = new HashMap<>();
        private InMemoryMemberRepository(WorkspaceMember... members) { for (WorkspaceMember member : members) insert(member); }
        private static String key(UUID workspaceId, SubjectId subjectId) { return workspaceId + "/" + subjectId.value(); }
        @Override public void insert(WorkspaceMember member) { items.put(key(member.workspaceId(), member.subjectId()), member); }
        @Override public Optional<WorkspaceMember> findByWorkspaceAndSubject(UUID workspaceId, SubjectId subjectId) {
            return Optional.ofNullable(items.get(key(workspaceId, subjectId)));
        }
        @Override public boolean activatePending(UUID workspaceId, SubjectId subjectId, Instant activatedAt) { return false; }
        @Override public boolean updateRole(UUID workspaceId, SubjectId subjectId, WorkspaceRole expectedRole, WorkspaceRole newRole, Instant updatedAt) { return false; }
        @Override public boolean deleteNonOwner(UUID workspaceId, SubjectId subjectId) { return false; }
        @Override public boolean demoteOwner(UUID workspaceId, SubjectId subjectId, Instant updatedAt) { return false; }
        @Override public boolean promoteActiveMemberToOwner(UUID workspaceId, SubjectId subjectId, WorkspaceRole expectedRole, Instant updatedAt) {
            WorkspaceMember current = findByWorkspaceAndSubject(workspaceId, subjectId).orElse(null);
            if (current == null || !current.isActive() || current.role() != expectedRole) return false;
            insert(new WorkspaceMember(current.workspaceId(), current.subjectId(), WorkspaceRole.OWNER,
                    current.status(), current.invitedBy(), current.createdAt(), current.activatedAt(), updatedAt));
            return true;
        }
        @Override public boolean hasActiveOwner(UUID workspaceId) { return owner(workspaceId, WorkspaceMemberStatus.ACTIVE); }
        @Override public boolean hasRevokedOwner(UUID workspaceId) { return owner(workspaceId, WorkspaceMemberStatus.REVOKED); }
        private boolean owner(UUID workspaceId, WorkspaceMemberStatus status) {
            return items.values().stream().anyMatch(member -> member.workspaceId().equals(workspaceId)
                    && member.role() == WorkspaceRole.OWNER && member.status() == status);
        }
    }

    private static final class InMemoryOwnerRecoveryRepository implements WorkspaceOwnerRecoveryRepository {
        private WorkspaceOwnerRecoveryRequest request;
        @Override public void expireOpenRequests(UUID workspaceId, Instant now) { }
        @Override public void insert(WorkspaceOwnerRecoveryRequest request) { this.request = request; }
        @Override public Optional<WorkspaceOwnerRecoveryRequest> findForUpdate(UUID requestId) {
            return request != null && request.id().equals(requestId) ? Optional.of(request) : Optional.empty();
        }
        @Override public boolean markExecuted(UUID requestId, String approvedBy, Instant executedAt) {
            if (request == null || !request.id().equals(requestId) || !"REQUESTED".equals(request.status())) return false;
            request = new WorkspaceOwnerRecoveryRequest(request.id(), request.workspaceId(), request.newOwnerSubjectId(),
                    request.requestedBy(), approvedBy, request.incidentReference(), "EXECUTED",
                    request.requestedAt(), request.expiresAt(), executedAt);
            return true;
        }
    }

    private static final class InMemorySecurityAuditRepository implements WorkspaceSecurityOperationAuditRepository {
        private final List<WorkspaceSecurityOperationAudit> items = new ArrayList<>();
        @Override public void insert(WorkspaceSecurityOperationAudit audit) { items.add(audit); }
    }

    private static final class InMemoryAuthorizationAuditRepository implements WorkspaceAuthorizationAuditRepository {
        private final List<WorkspaceAuthorizationAudit> items = new ArrayList<>();
        @Override public void insert(WorkspaceAuthorizationAudit audit) { items.add(audit); }
        @Override public WorkspaceAuthorizationAuditPage findPage(UUID workspaceId, int page, int size, long offset) {
            return new WorkspaceAuthorizationAuditPage(List.of(), page, size, 0);
        }
        @Override public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) { return 0; }
        @Override public List<WorkspaceAuthorizationAudit> exportAfter(UUID workspaceId,
                WorkspaceAuthorizationAuditCursor cursor, int limit) {
            return items.stream().filter(audit -> audit.workspaceId().equals(workspaceId))
                    .sorted(java.util.Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt)
                            .thenComparing(WorkspaceAuthorizationAudit::id))
                    .filter(audit -> cursor == null
                            || audit.occurredAt().isAfter(cursor.occurredAt())
                            || (audit.occurredAt().equals(cursor.occurredAt())
                            && audit.id().compareTo(cursor.id()) > 0))
                    .limit(limit).toList();
        }
        @Override public WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince) {
            return new WorkspaceAuthorizationAuditOperationalStatus(0, 0, 0, 0, null);
        }
    }
}
