package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

public interface WorkspaceMemberMapper {

    int insert(WorkspaceMemberRow row);

    WorkspaceMemberRow selectByWorkspaceAndSubject(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("subjectId") String subjectId);

    int activatePending(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("subjectId") String subjectId,
            @Param("activatedAt") Instant activatedAt);

    int updateRole(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("subjectId") String subjectId,
            @Param("expectedRole") WorkspaceRole expectedRole,
            @Param("newRole") WorkspaceRole newRole,
            @Param("updatedAt") Instant updatedAt);

    int deleteNonOwner(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("subjectId") String subjectId);

    int demoteOwner(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("ownerSubjectId") String ownerSubjectId,
            @Param("updatedAt") Instant updatedAt);

    int promoteActiveMemberToOwner(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId,
            @Param("subjectId") String subjectId,
            @Param("expectedRole") WorkspaceRole expectedRole,
            @Param("updatedAt") Instant updatedAt);

    boolean hasActiveOwner(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId);

    boolean hasRevokedOwner(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") UUID workspaceId);
}
