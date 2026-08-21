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

    /** SKIP LOCKED 领取到期的 PENDING 任务（返回带锁的行）。 */
    List<TaskJob> claimReadyJobs(String lockedBy, int batchSize, Instant now);

    /** 执行完成/失败后的状态更新。 */
    boolean completeJob(UUID id, String status, @Nullable String lastError,
            @Nullable Instant nextRunAt, Instant now);

    boolean cancelJob(UUID id, Instant now);

    boolean retryJob(UUID id, Instant nextRunAt, Instant now);

    /** 启动时重置僵尸 RUNNING（locked_at 超时）。 */
    int resetZombieRunning(Instant cutoff, Instant now);

    void insertAudit(dev.ainer.module.task.tasks.domain.TaskAudit audit);
}
