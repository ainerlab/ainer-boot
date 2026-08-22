package dev.ainer.module.task.tasks.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * SKIP LOCKED 领取的专用 Mapper（ADR-0047 §3）。与通用 {@link TaskMapper} 分离，
 * 因为领取是多语句原子操作（CTE + UPDATE + INSERT + SELECT），不适合放在通用 XML 里。
 */
@Mapper
public interface TaskClaimMapper {

    /**
     * 原子领取：单条语句内完成「选出到期任务 → UPDATE 为 RUNNING + 锁定 →
     * 写入同事务的 CLAIMED 审计行 → 返回锁定行」。SKIP LOCKED 保证多实例下不重复领取；
     * 审计主键由数据库 {@code uuidv7()} 默认值生成，满足表的 v7 CHECK 约束。
     * 领取对象包括到期 PENDING 与退避到期（next_run_at 已过）的 FAILED；
     * 同时返回定义上的 {@code timeout_seconds}，供引擎执行超时看门狗使用。
     */
    @Select("""
            WITH ready AS (
                SELECT id FROM ainer_task_job
                WHERE (status = 'PENDING'
                       OR (status = 'FAILED' AND next_run_at <= #{now}))
                  AND next_run_at <= #{now}
                ORDER BY next_run_at
                LIMIT #{batchSize}
                FOR UPDATE SKIP LOCKED
            ),
            claimed AS (
                UPDATE ainer_task_job j
                SET status = 'RUNNING',
                    locked_by = #{lockedBy},
                    locked_at = #{now},
                    attempt_count = j.attempt_count + 1,
                    updated_at = #{now}
                FROM ready,
                     ainer_task_definition d
                WHERE j.id = ready.id
                  AND d.task_type = j.task_type
                RETURNING j.id, j.task_type, j.payload::text AS payload_json, j.status,
                          j.attempt_count, j.max_attempts, j.next_run_at, j.interval_seconds,
                          j.locked_by, j.locked_at, j.last_error,
                          j.created_by_issuer, j.created_by_type, j.created_by_id,
                          j.created_at, j.updated_at, j.completed_at,
                          d.timeout_seconds
            ),
            audited AS (
                INSERT INTO ainer_task_audit
                    (job_id, event, attempt, actor_issuer, actor_type, actor_id, occurred_at)
                SELECT c.id, 'CLAIMED', c.attempt_count, 'system', 'SERVICE', #{lockedBy}, #{now}
                FROM claimed c
            )
            SELECT id, task_type, payload_json, status, attempt_count, max_attempts,
                   next_run_at, interval_seconds, locked_by, locked_at, last_error,
                   created_by_issuer, created_by_type, created_by_id,
                   created_at, updated_at, completed_at, timeout_seconds
            FROM claimed
            """)
    List<TaskJobRow> claimReadyJobs(@Param("lockedBy") String lockedBy,
            @Param("batchSize") int batchSize, @Param("now") Instant now);
}
