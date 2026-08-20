package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.WorkspaceMember;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Workspace 成员的持久化端口。
 *
 * <p>所有成员状态变更都是条件更新（携带期望的前置状态），返回 boolean 表示是否命中，
 * 由应用层据此判定并发冲突。OWNER 相关操作必须使用 {@code demoteOwner} 与
 * {@code promoteActiveMemberToOwner} 这类专用方法，保证任何时刻至多一个 ACTIVE OWNER。
 */
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
