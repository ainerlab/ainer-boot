package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.UUID;

public interface AiTaskMapper {

    int insertTask(AiTaskRow task);

    AiTaskRow selectTaskById(@Param("id") UUID id);

    int updateTaskStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("updatedAt") Instant updatedAt);

    int insertContextSnapshot(AiContextSnapshotRow snapshot);

    int insertTaskRun(AiTaskRunRow run);

    int updateTaskRunStatus(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("completedAt") Instant completedAt);

    AiTaskRunRow selectTaskRunById(@Param("id") UUID id);

    int insertResult(AiResultRow result);

    AiResultRow selectResultByRunId(@Param("runId") UUID runId);

    int insertFeedback(AiFeedbackRow feedback);
}
