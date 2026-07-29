package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiFeedback;
import dev.ainer.module.ai.gateway.domain.AiResult;
import dev.ainer.module.ai.gateway.domain.AiTask;
import dev.ainer.module.ai.gateway.domain.AiTaskRun;
import dev.ainer.module.ai.gateway.domain.AiTaskStatus;
import dev.ainer.module.ai.gateway.domain.ContextSnapshot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiTaskRepository {

    void insertTask(AiTask task);

    Optional<AiTask> findTask(UUID id);

    boolean updateTaskStatus(UUID id, AiTaskStatus expected, AiTaskStatus next, Instant updatedAt);

    void insertContextSnapshot(ContextSnapshot snapshot);

    void insertTaskRun(AiTaskRun run);

    boolean updateTaskRunStatus(UUID id, String status, Instant completedAt);

    Optional<AiTaskRun> findTaskRun(UUID id);

    void insertResult(AiResult result);

    Optional<AiResult> findResultByRun(UUID runId);

    void insertFeedback(AiFeedback feedback);
}
