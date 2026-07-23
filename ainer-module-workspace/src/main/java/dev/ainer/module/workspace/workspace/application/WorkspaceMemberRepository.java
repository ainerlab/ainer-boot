package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository {

    void insert(WorkspaceMember member);

    Optional<WorkspaceMember> findByWorkspaceAndSubject(
            TenantId tenantId, UUID workspaceId, SubjectId subjectId);

    boolean activatePending(
            TenantId tenantId, UUID workspaceId, SubjectId subjectId, Instant activatedAt);

    boolean updateRole(
            TenantId tenantId,
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            WorkspaceRole newRole,
            Instant updatedAt);

    boolean deleteNonOwner(TenantId tenantId, UUID workspaceId, SubjectId subjectId);

    boolean demoteOwner(
            TenantId tenantId, UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt);

    boolean promoteActiveMemberToOwner(
            TenantId tenantId,
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            Instant updatedAt);

    boolean hasActiveOwner(TenantId tenantId, UUID workspaceId);

    boolean hasRevokedOwner(TenantId tenantId, UUID workspaceId);
}
