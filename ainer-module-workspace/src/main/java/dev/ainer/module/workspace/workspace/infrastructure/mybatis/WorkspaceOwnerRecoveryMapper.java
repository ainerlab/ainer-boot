package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

public interface WorkspaceOwnerRecoveryMapper {

    int expireOpenRequests(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") Instant now);

    int insert(WorkspaceOwnerRecoveryRequestRow row);

    WorkspaceOwnerRecoveryRequestRow selectForUpdate(@Param("requestId") UUID requestId);

    int markExecuted(
            @Param("requestId") UUID requestId,
            @Param("approvedBy") String approvedBy,
            @Param("executedAt") Instant executedAt);
}
