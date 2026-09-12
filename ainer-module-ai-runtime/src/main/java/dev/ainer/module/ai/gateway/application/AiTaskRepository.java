package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiFeedback;
import dev.ainer.module.ai.gateway.domain.AiResult;
import dev.ainer.module.ai.gateway.domain.AiTask;
import dev.ainer.module.ai.gateway.domain.AiTaskRun;
import dev.ainer.module.ai.gateway.domain.AiTaskRunStatus;
import dev.ainer.module.ai.gateway.domain.AiTaskStatus;
import dev.ainer.module.ai.gateway.domain.ContextSnapshot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * AI Task 域的持久化端口：任务、上下文快照、任务运行、结果与反馈的写入与查询。
 */
public interface AiTaskRepository {

    void insertTask(AiTask task);

    Optional<AiTask> findTask(UUID id);

    boolean updateTaskStatus(UUID id, AiTaskStatus expected, AiTaskStatus next, Instant updatedAt);

    void insertContextSnapshot(ContextSnapshot snapshot);

    void insertTaskRun(AiTaskRun run);

    /**
     * 任务运行状态回写（带期望态 = CAS）。期望态不匹配返回 false，调用方必须据此判断，
     * 避免自愈/重试已推进终态后被迟到的写回覆盖。
     */
    boolean updateTaskRunStatus(UUID id, AiTaskRunStatus expected, AiTaskRunStatus next, Instant completedAt);

    /**
     * 把超过阈值仍停在 RUNNING 的任务运行批量推进到 FAILED，返回实际处理行数。
     * 幂等且并发安全（条件 UPDATE 充当 CAS + FOR UPDATE SKIP LOCKED 领取候选行）。
     */
    int healStuckRunningRuns(Instant startedBefore, int limit, Instant healedAt);

    /** 仍停在 RUNNING 且早于阈值的任务运行数（自愈积压）。 */
    long countStuckRunningRuns(Instant startedBefore);

    /** 仍停在 RUNNING 的最早 started_at；没有则返回 {@code null}。 */
    Instant oldestRunningRunStartedAt();

    /**
     * 把超过阈值仍停在 RUNNING 的任务批量推进到 FAILED，返回实际处理行数。
     * 任务表没有 started_at，用 updated_at 作为中间态年龄基准。
     */
    int healStuckRunningTasks(Instant updatedBefore, int limit, Instant healedAt);

    /** 仍停在 RUNNING 且 updated_at 早于阈值的任务数（自愈积压）。 */
    long countStuckRunningTasks(Instant updatedBefore);

    /** 仍停在 RUNNING 的任务的最早 updated_at；没有则返回 {@code null}。 */
    Instant oldestRunningTaskUpdatedAt();

    Optional<AiTaskRun> findTaskRun(UUID id);

    void insertResult(AiResult result);

    Optional<AiResult> findResultByRun(UUID runId);

    void insertFeedback(AiFeedback feedback);
}
