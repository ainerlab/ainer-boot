package dev.ainer.module.task.tasks.application;

import dev.ainer.module.task.tasks.domain.TaskDefinition;
import dev.ainer.module.task.tasks.domain.TaskJob;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 任务持久化端口（ADR-0047）。 */
public interface TaskRepository {

    void insertDefinition(TaskDefinition definition);

    Optional<TaskDefinition> findDefinitionByType(String taskType);

    List<TaskDefinition> pageDefinitions(long offset, int limit);

    long countDefinitions();

    boolean updateDefinitionStatus(String taskType, String status, Instant now);

    void insertJob(TaskJob job);

    Optional<TaskJob> findJob(UUID id);

    List<TaskJob> pageJobs(@Nullable String status, @Nullable String taskType,
            long offset, int limit);

    long countJobs(@Nullable String status, @Nullable String taskType);

    boolean completeJob(UUID id, String status, @Nullable String lastError,
            @Nullable Instant nextRunAt, Instant now);

    boolean cancelJob(UUID id, Instant now);

    boolean retryJob(UUID id, Instant nextRunAt, Instant now);

    /**
     * 僵尸清扫：把 {@code locked_at} 早于「定义 {@code timeout_seconds} × multiplier」的
     * RUNNING 任务重置回 PENDING 并清空租约，返回重置行数。
     */
    int resetZombieRunning(Instant now, int multiplier);

    void insertAudit(dev.ainer.module.task.tasks.domain.TaskAudit audit);
}
