package dev.ainer.module.task.tasks.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TaskMapper {

    void insertDefinition(TaskDefinitionRow row);

    TaskDefinitionRow selectDefinitionByType(@Param("taskType") String taskType);

    List<TaskDefinitionRow> pageDefinitions(@Param("offset") long offset, @Param("limit") int limit);

    long countDefinitions();

    int updateDefinitionStatus(@Param("taskType") String taskType,
            @Param("status") String status, @Param("now") Instant now);

    void insertJob(TaskJobRow row);

    TaskJobRow selectJob(@Param("id") UUID id);

    List<TaskJobRow> pageJobs(@Param("status") String status, @Param("taskType") String taskType,
            @Param("offset") long offset, @Param("limit") int limit);

    long countJobs(@Param("status") String status, @Param("taskType") String taskType);

    int completeJob(@Param("id") UUID id, @Param("status") String status,
            @Param("lastError") String lastError, @Param("nextRunAt") Instant nextRunAt,
            @Param("now") Instant now);

    int cancelJob(@Param("id") UUID id, @Param("now") Instant now);

    int retryJob(@Param("id") UUID id, @Param("nextRunAt") Instant nextRunAt,
            @Param("now") Instant now);

    int resetZombieRunning(@Param("now") Instant now, @Param("multiplier") int multiplier);

    void insertAudit(@Param("id") UUID id, @Param("jobId") UUID jobId,
            @Param("event") String event, @Param("attempt") Integer attempt,
            @Param("actorIssuer") String actorIssuer, @Param("actorType") String actorType,
            @Param("actorId") String actorId, @Param("detail") String detail,
            @Param("occurredAt") Instant occurredAt);
}
