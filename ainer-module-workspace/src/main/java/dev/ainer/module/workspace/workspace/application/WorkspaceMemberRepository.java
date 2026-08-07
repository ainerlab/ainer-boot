package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository {

    void insert(WorkspaceMember member);

    Optional<WorkspaceMember> findByWorkspaceAndSubject(
            UUID workspaceId, SubjectId subjectId);

    boolean activatePending(
            UUID workspaceId, SubjectId subjectId, Instant activatedAt);

    boolean updateRole(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            WorkspaceRole newRole,
            Instant updatedAt);

    boolean deleteNonOwner(UUID workspaceId, SubjectId subjectId);

    boolean demoteOwner(
            UUID workspaceId, SubjectId ownerSubjectId, Instant updatedAt);

    boolean promoteActiveMemberToOwner(
            UUID workspaceId,
            SubjectId subjectId,
            WorkspaceRole expectedRole,
            Instant updatedAt);

    boolean hasActiveOwner(UUID workspaceId);

    boolean hasRevokedOwner(UUID workspaceId);
}
