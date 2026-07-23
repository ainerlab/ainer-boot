package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkspaceMapper {

    int insert(WorkspaceRow row);

    int updateName(
            @Param("tenantId") String tenantId,
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);

    WorkspaceRow selectById(@Param("tenantId") String tenantId, @Param("id") UUID id);

    WorkspaceRow selectByIdForUpdate(@Param("tenantId") String tenantId, @Param("id") UUID id);

    List<WorkspaceRow> selectPage(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long count(@Param("tenantId") String tenantId, @Param("subjectId") String subjectId);
}
