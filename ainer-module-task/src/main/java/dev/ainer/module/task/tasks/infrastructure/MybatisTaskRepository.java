package dev.ainer.module.task.tasks.infrastructure;

import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.task.tasks.application.TaskRepository;
import dev.ainer.module.task.tasks.domain.TaskAudit;
import dev.ainer.module.task.tasks.domain.TaskDefinition;
import dev.ainer.module.task.tasks.domain.TaskJob;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MyBatis 适配器（ADR-0047）。 */
@Repository
public class MybatisTaskRepository implements TaskRepository {

    private final TaskMapper mapper;

    public MybatisTaskRepository(TaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertDefinition(TaskDefinition definition) {
        TaskDefinitionRow row = new TaskDefinitionRow();
        row.setId(definition.id());
        row.setTaskType(definition.taskType());
        row.setDisplayName(definition.displayName());
        row.setHandlerRef(definition.handlerRef());
        row.setMaxAttempts(definition.maxAttempts());
        row.setTimeoutSeconds(definition.timeoutSeconds());
        row.setStatus(definition.status());
        row.setCreatedAt(definition.createdAt());
        row.setUpdatedAt(definition.updatedAt());
        mapper.insertDefinition(row);
    }

    @Override
    public Optional<TaskDefinition> findDefinitionByType(String taskType) {
        return Optional.ofNullable(mapper.selectDefinitionByType(taskType))
                .map(MybatisTaskRepository::toDefinition);
    }

    @Override
    public List<TaskDefinition> pageDefinitions(long offset, int limit) {
        return mapper.pageDefinitions(offset, limit).stream()
                .map(MybatisTaskRepository::toDefinition).toList();
    }

    @Override
    public long countDefinitions() {
        return mapper.countDefinitions();
    }

    @Override
    public boolean updateDefinitionStatus(String taskType, String status, Instant now) {
        return mapper.updateDefinitionStatus(taskType, status, now) == 1;
    }

    @Override
    public void insertJob(TaskJob job) {
        TaskJobRow row = new TaskJobRow();
        row.setId(job.id());
        row.setTaskType(job.taskType());
        row.setPayloadJson(job.payloadJson());
        row.setStatus(job.status());
        row.setAttemptCount(job.attemptCount());
        row.setMaxAttempts(job.maxAttempts());
        row.setNextRunAt(job.nextRunAt());
        row.setIntervalSeconds(job.intervalSeconds());
        row.setCreatedByIssuer(job.createdByIssuer());
        row.setCreatedByType(job.createdByType());
        row.setCreatedById(job.createdById());
        row.setCreatedAt(job.createdAt());
        row.setUpdatedAt(job.updatedAt());
        mapper.insertJob(row);
    }

    @Override
    public Optional<TaskJob> findJob(UUID id) {
        return Optional.ofNullable(mapper.selectJob(id)).map(MybatisTaskRepository::toJob);
    }

    @Override
    public List<TaskJob> pageJobs(@Nullable String status, @Nullable String taskType,
            long offset, int limit) {
        return mapper.pageJobs(status, taskType, offset, limit).stream()
                .map(MybatisTaskRepository::toJob).toList();
    }

    @Override
    public long countJobs(@Nullable String status, @Nullable String taskType) {
        return mapper.countJobs(status, taskType);
    }

    @Override
    public boolean completeJob(UUID id, String status, @Nullable String lastError,
            @Nullable Instant nextRunAt, Instant now) {
        return mapper.completeJob(id, status, lastError, nextRunAt, now) == 1;
    }

    @Override
    public boolean cancelJob(UUID id, Instant now) {
        return mapper.cancelJob(id, now) == 1;
    }

    @Override
    public boolean retryJob(UUID id, Instant nextRunAt, Instant now) {
        return mapper.retryJob(id, nextRunAt, now) == 1;
    }

    @Override
    public int resetZombieRunning(Instant now, int multiplier) {
        return mapper.resetZombieRunning(now, multiplier);
    }

    @Override
    public void insertAudit(TaskAudit audit) {
        mapper.insertAudit(audit.id(), audit.jobId(), audit.event(), audit.attempt(),
                audit.actorIssuer(), audit.actorType(), audit.actorId(), audit.detail(),
                audit.occurredAt());
    }

    private static TaskDefinition toDefinition(TaskDefinitionRow row) {
        return new TaskDefinition(row.getId(), row.getTaskType(), row.getDisplayName(),
                row.getHandlerRef(), row.getMaxAttempts(), row.getTimeoutSeconds(),
                row.getStatus(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private static TaskJob toJob(TaskJobRow row) {
        return new TaskJob(row.getId(), row.getTaskType(), row.getPayloadJson(),
                row.getStatus(), row.getAttemptCount(), row.getMaxAttempts(),
                row.getNextRunAt(), row.getIntervalSeconds(), row.getLockedBy(),
                row.getLockedAt(), row.getLastError(),
                row.getCreatedByIssuer(), row.getCreatedByType(), row.getCreatedById(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getCompletedAt());
    }
}
