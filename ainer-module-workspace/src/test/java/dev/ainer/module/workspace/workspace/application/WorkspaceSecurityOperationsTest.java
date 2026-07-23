package dev.ainer.module.workspace.workspace.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceSecurityOperationsTest {

    private static final Instant NOW = Instant.parse("2026-07-23T05:00:00Z");
    private static final TenantId TENANT = new TenantId("tenant:security-ops");

    @Test
    void ownerRecoveryRequiresSeparationOfDutiesAndKeepsRevokedOwnerRevoked() {
        UUID workspaceId = UUID.randomUUID();
        SubjectId revokedOwner = new SubjectId("subject:revoked-owner");
        SubjectId target = new SubjectId("subject:active-admin");
        InMemoryWorkspaceRepository workspaces = new InMemoryWorkspaceRepository(
                Workspace.create(workspaceId, TENANT, new WorkspaceName("恢复空间"), NOW.minusSeconds(3600)));
        InMemoryMemberRepository members = new InMemoryMemberRepository(
                member(workspaceId, revokedOwner, WorkspaceRole.OWNER, WorkspaceMemberStatus.REVOKED),
                member(workspaceId, target, WorkspaceRole.ADMIN, WorkspaceMemberStatus.ACTIVE));
        InMemoryOwnerRecoveryRepository recoveries = new InMemoryOwnerRecoveryRepository();
        InMemorySecurityAuditRepository audits = new InMemorySecurityAuditRepository();
        WorkspaceOwnerRecoveryService service = new WorkspaceOwnerRecoveryService(
                workspaces, members, recoveries, audits, Clock.fixed(NOW, ZoneOffset.UTC));

        WorkspaceOwnerRecoveryRequest request = service.requestRecovery(
                "operator:request", TENANT, workspaceId, target,
                "INC-OWNER-42", Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:request", TENANT, request.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_APPROVER_MUST_DIFFER));

        WorkspaceOwnerRecoveryRequest executed = service.approveAndExecute(
                "operator:approve", TENANT, request.id());

        assertThat(executed.status()).isEqualTo("EXECUTED");
        assertThat(members.findByWorkspaceAndSubject(TENANT, workspaceId, target).orElseThrow().role())
                .isEqualTo(WorkspaceRole.OWNER);
        assertThat(members.findByWorkspaceAndSubject(TENANT, workspaceId, revokedOwner).orElseThrow().status())
                .isEqualTo(WorkspaceMemberStatus.REVOKED);
        assertThat(audits.items).extracting(WorkspaceSecurityOperationAudit::phase)
                .containsExactly("REQUESTED", "EXECUTED");
        assertThatThrownBy(() -> service.approveAndExecute(
                "operator:third", TENANT, request.id()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_CONFLICT));
    }

    @Test
    void ownerRecoveryFailsClosedWhenAnActiveOwnerExistsOrTenantDiffers() {
        UUID workspaceId = UUID.randomUUID();
        SubjectId owner = new SubjectId("subject:owner");
        SubjectId target = new SubjectId("subject:admin");
        InMemoryWorkspaceRepository workspaces = new InMemoryWorkspaceRepository(
                Workspace.create(workspaceId, TENANT, new WorkspaceName("正常空间"), NOW.minusSeconds(60)));
        InMemoryMemberRepository members = new InMemoryMemberRepository(
                member(workspaceId, owner, WorkspaceRole.OWNER, WorkspaceMemberStatus.ACTIVE),
                member(workspaceId, target, WorkspaceRole.ADMIN, WorkspaceMemberStatus.ACTIVE));
        WorkspaceOwnerRecoveryService service = new WorkspaceOwnerRecoveryService(
                workspaces, members, new InMemoryOwnerRecoveryRepository(),
                new InMemorySecurityAuditRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.requestRecovery(
                "operator:request", TENANT, workspaceId, target,
                "INC-NOT-NEEDED", Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_NOT_REQUIRED));
        assertThatThrownBy(() -> service.requestRecovery(
                "operator:request", new TenantId("tenant:other"), workspaceId, target,
                "INC-CROSS-TENANT", Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkspaceErrorCode.OWNER_RECOVERY_NOT_FOUND));
    }

    @Test
    void auditExportUsesStableCursorAndRecordsEveryBatchAccess() {
        InMemoryAuthorizationAuditRepository repository = new InMemoryAuthorizationAuditRepository();
        InMemorySecurityAuditRepository operations = new InMemorySecurityAuditRepository();
        UUID workspaceId = UUID.randomUUID();
        repository.hot.add(audit("00000000-0000-0000-0000-000000000001", workspaceId, NOW.minusSeconds(30)));
        repository.hot.add(audit("00000000-0000-0000-0000-000000000002", workspaceId, NOW.minusSeconds(20)));
        repository.hot.add(audit("00000000-0000-0000-0000-000000000003", workspaceId, NOW.minusSeconds(10)));
        WorkspaceAuthorizationAuditLifecycleService service = new WorkspaceAuthorizationAuditLifecycleService(
                repository, operations, Clock.fixed(NOW, ZoneOffset.UTC));

        WorkspaceAuthorizationAuditExportBatch first = service.export(
                "siem:exporter", TENANT, null, 2);
        WorkspaceAuthorizationAuditExportBatch second = service.export(
                "siem:exporter", TENANT, first.nextCursor(), 2);

        assertThat(first.items()).extracting(WorkspaceAuthorizationAudit::id)
                .containsExactly(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).extracting(WorkspaceAuthorizationAudit::id)
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assertThat(second.hasMore()).isFalse();
        assertThat(operations.items)
                .filteredOn(audit -> "AUTHORIZATION_AUDIT_EXPORT".equals(audit.operationType()))
                .extracting(WorkspaceSecurityOperationAudit::recordCount)
                .containsExactly(2, 1);

        assertThat(service.archiveBefore(NOW.minusSeconds(15), 100)).isEqualTo(2);
        assertThat(service.status(NOW.minusSeconds(60)))
                .extracting(
                        WorkspaceAuthorizationAuditOperationalStatus::hot,
                        WorkspaceAuthorizationAuditOperationalStatus::archived)
                .containsExactly(1L, 2L);
        assertThat(service.export("siem:exporter", TENANT, null, 10).items()).hasSize(3);
    }

    private static WorkspaceMember member(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole role,
            WorkspaceMemberStatus status) {
        Instant activatedAt = status == WorkspaceMemberStatus.PENDING ? null : NOW.minusSeconds(1800);
        return new WorkspaceMember(
                TENANT, workspaceId, subjectId, role, status, subjectId,
                NOW.minusSeconds(1800), activatedAt, NOW.minusSeconds(60));
    }

    private static WorkspaceAuthorizationAudit audit(String id, UUID workspaceId, Instant occurredAt) {
        return new WorkspaceAuthorizationAudit(
                UUID.fromString(id), TENANT.value(), workspaceId, "subject:actor", null,
                WorkspaceAuthorizationAction.WORKSPACE_READ,
                WorkspaceAuthorizationDecision.ALLOWED,
                "AINER.COMMON.OK",
                occurredAt);
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<UUID, Workspace> items = new LinkedHashMap<>();

        private InMemoryWorkspaceRepository(Workspace workspace) {
            items.put(workspace.id(), workspace);
        }

        @Override public void insert(Workspace workspace) { items.put(workspace.id(), workspace); }
        @Override public boolean update(Workspace workspace, long expectedVersion) { items.put(workspace.id(), workspace); return true; }
        @Override public Optional<Workspace> findById(TenantId tenantId, UUID id) {
            return Optional.ofNullable(items.get(id)).filter(item -> item.tenantId().equals(tenantId));
        }
        @Override public Optional<Workspace> findByIdForUpdate(TenantId tenantId, UUID id) { return findById(tenantId, id); }
        @Override public WorkspacePage findPage(TenantId tenantId, SubjectId subjectId, int page, int size, long offset) {
            return new WorkspacePage(List.of(), page, size, 0);
        }
    }

    private static final class InMemoryMemberRepository implements WorkspaceMemberRepository {
        private final Map<String, WorkspaceMember> items = new LinkedHashMap<>();

        private InMemoryMemberRepository(WorkspaceMember... members) {
            for (WorkspaceMember member : members) insert(member);
        }

        private String key(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return tenantId.value() + "/" + workspaceId + "/" + subjectId.value();
        }

        @Override public void insert(WorkspaceMember member) { items.put(key(member.tenantId(), member.workspaceId(), member.subjectId()), member); }
        @Override public Optional<WorkspaceMember> findByWorkspaceAndSubject(TenantId tenantId, UUID workspaceId, SubjectId subjectId) {
            return Optional.ofNullable(items.get(key(tenantId, workspaceId, subjectId)));
        }
        @Override public boolean activatePending(TenantId tenantId, UUID workspaceId, SubjectId subjectId, Instant activatedAt) { return false; }
        @Override public boolean updateRole(TenantId tenantId, UUID workspaceId, SubjectId subjectId, WorkspaceRole expectedRole, WorkspaceRole newRole, Instant updatedAt) { return false; }
        @Override public boolean deleteNonOwner(TenantId tenantId, UUID workspaceId, SubjectId subjectId) { return false; }
        @Override public boolean demoteOwner(TenantId tenantId, UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt) { return false; }
        @Override public boolean promoteActiveMemberToOwner(TenantId tenantId, UUID workspaceId, SubjectId subjectId, WorkspaceRole expectedRole, Instant updatedAt) {
            WorkspaceMember current = findByWorkspaceAndSubject(tenantId, workspaceId, subjectId).orElse(null);
            if (current == null || !current.isActive() || current.role() != expectedRole || current.role() == WorkspaceRole.OWNER) return false;
            insert(new WorkspaceMember(
                    current.tenantId(), current.workspaceId(), current.subjectId(), WorkspaceRole.OWNER,
                    current.status(), current.invitedBy(), current.createdAt(), current.activatedAt(), updatedAt));
            return true;
        }
        @Override public boolean hasActiveOwner(TenantId tenantId, UUID workspaceId) { return owner(tenantId, workspaceId, WorkspaceMemberStatus.ACTIVE); }
        @Override public boolean hasRevokedOwner(TenantId tenantId, UUID workspaceId) { return owner(tenantId, workspaceId, WorkspaceMemberStatus.REVOKED); }
        private boolean owner(TenantId tenantId, UUID workspaceId, WorkspaceMemberStatus status) {
            return items.values().stream().anyMatch(member -> member.tenantId().equals(tenantId)
                    && member.workspaceId().equals(workspaceId) && member.role() == WorkspaceRole.OWNER
                    && member.status() == status);
        }
    }

    private static final class InMemoryOwnerRecoveryRepository implements WorkspaceOwnerRecoveryRepository {
        private WorkspaceOwnerRecoveryRequest request;
        @Override public void expireOpenRequests(TenantId tenantId, UUID workspaceId, Instant now) { }
        @Override public void insert(WorkspaceOwnerRecoveryRequest request) { this.request = request; }
        @Override public Optional<WorkspaceOwnerRecoveryRequest> findForUpdate(TenantId tenantId, UUID requestId) {
            return request != null && request.id().equals(requestId) && request.tenantId().equals(tenantId)
                    ? Optional.of(request) : Optional.empty();
        }
        @Override public boolean markExecuted(UUID requestId, String approvedBy, Instant executedAt) {
            if (request == null || !request.id().equals(requestId) || !"REQUESTED".equals(request.status())) return false;
            request = new WorkspaceOwnerRecoveryRequest(
                    request.id(), request.tenantId(), request.workspaceId(), request.newOwnerSubjectId(),
                    request.requestedBy(), approvedBy, request.incidentReference(), "EXECUTED",
                    request.requestedAt(), request.expiresAt(), executedAt);
            return true;
        }
    }

    private static final class InMemorySecurityAuditRepository implements WorkspaceSecurityOperationAuditRepository {
        private final List<WorkspaceSecurityOperationAudit> items = new ArrayList<>();
        @Override public void insert(WorkspaceSecurityOperationAudit audit) { items.add(audit); }
    }

    private static final class InMemoryAuthorizationAuditRepository
            implements WorkspaceAuthorizationAuditRepository {
        private final List<WorkspaceAuthorizationAudit> hot = new ArrayList<>();
        private final List<WorkspaceAuthorizationAudit> archived = new ArrayList<>();
        @Override public void insert(WorkspaceAuthorizationAudit audit) { hot.add(audit); }
        @Override public WorkspaceAuthorizationAuditPage findPage(TenantId tenantId, UUID workspaceId, int page, int size, long offset) {
            List<WorkspaceAuthorizationAudit> items = all().stream()
                    .filter(audit -> audit.tenantId().equals(tenantId.value()) && workspaceId.equals(audit.workspaceId()))
                    .sorted(Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt).reversed()
                            .thenComparing(WorkspaceAuthorizationAudit::id).reversed())
                    .toList();
            return new WorkspaceAuthorizationAuditPage(items, page, size, items.size());
        }
        @Override public int archiveBefore(Instant cutoff, Instant archivedAt, int batchSize) {
            List<WorkspaceAuthorizationAudit> selected = hot.stream()
                    .filter(audit -> audit.occurredAt().isBefore(cutoff))
                    .sorted(Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt)
                            .thenComparing(WorkspaceAuthorizationAudit::id))
                    .limit(batchSize).toList();
            archived.addAll(selected);
            hot.removeAll(selected);
            return selected.size();
        }
        @Override public List<WorkspaceAuthorizationAudit> exportAfter(TenantId tenantId, WorkspaceAuthorizationAuditCursor cursor, int limit) {
            return all().stream().filter(audit -> audit.tenantId().equals(tenantId.value()))
                    .filter(audit -> cursor == null || audit.occurredAt().isAfter(cursor.occurredAt())
                            || (audit.occurredAt().equals(cursor.occurredAt()) && audit.id().compareTo(cursor.id()) > 0))
                    .sorted(Comparator.comparing(WorkspaceAuthorizationAudit::occurredAt)
                            .thenComparing(WorkspaceAuthorizationAudit::id))
                    .limit(limit).toList();
        }
        @Override public WorkspaceAuthorizationAuditOperationalStatus operationalStatus(Instant deniedSince) {
            long denied = all().stream().filter(audit -> audit.decision() == WorkspaceAuthorizationDecision.DENIED)
                    .filter(audit -> !audit.occurredAt().isBefore(deniedSince)).count();
            return new WorkspaceAuthorizationAuditOperationalStatus(
                    hot.size(), archived.size(), denied, 0,
                    hot.stream().map(WorkspaceAuthorizationAudit::occurredAt).min(Instant::compareTo).orElse(null));
        }
        private List<WorkspaceAuthorizationAudit> all() {
            List<WorkspaceAuthorizationAudit> all = new ArrayList<>(hot);
            all.addAll(archived);
            return all;
        }
    }
}
