package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface WorkspaceAuthorizationAuditMapper {

    int insert(WorkspaceAuthorizationAuditRow row);

    List<WorkspaceAuthorizationAuditRow> selectPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long count(@Param("workspaceId") UUID workspaceId);

    int archiveBefore(
            @Param("cutoff") Instant cutoff,
            @Param("archivedAt") Instant archivedAt,
            @Param("limit") int limit);

    List<WorkspaceAuthorizationAuditRow> exportAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("afterOccurredAt") Instant afterOccurredAt,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    WorkspaceAuthorizationAuditOperationalStatusRow selectOperationalStatus(
            @Param("deniedSince") Instant deniedSince);
}
