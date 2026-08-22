package dev.ainer.module.task.tasks.infrastructure;

import dev.ainer.module.task.tasks.application.TaskEngineProperties;
import dev.ainer.module.task.tasks.domain.TaskJob;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.AutoCloseable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务执行引擎（ADR-0047 §3）：固定间隔轮询领取到期 PENDING 任务，virtual thread 提交
 * 执行，失败按指数退避重试，周期任务成功后重置。
 *
 * <p>产品通过实现 {@link TaskHandler} 端口注册处理器；引擎按 {@code task_type} 派发。
 * 未注册 {@code task_type} 的任务会失败并退避重试（不丢弃）。
 *
 * <p>多实例安全：PostgreSQL SKIP LOCKED 保证同一任务只被一个实例领取；领取与 CLAIMED
 * 审计在 {@link TaskClaimMapper} 的单条语句内原子完成。所有状态迁移都以
 * {@code status = 'RUNNING'} 条件更新（CAS），迟到的执行结果不会覆盖后续状态。
 *
 * <p>超时语义（ADR-0047 §3）：{@code timeout_seconds} 到期后引擎把任务标记为 FAILED 并
 * 进入退避重试，不杀死执行线程——虚拟线程自然结束后其结果因状态已迁移而被丢弃。因此
 * 超时后同一任务可能被重新领取并再次执行（at-least-once），处理器必须自行保证幂等。
 *
 * <p>僵尸自愈：每次轮询同步执行一次僵尸清扫，把 {@code locked_at} 早于
 * 「定义 {@code timeout_seconds} × {@link TaskEngineProperties#zombieCutoffMultiplier()}」
 * 的 RUNNING 任务重置回 PENDING，覆盖实例崩溃或线程挂死的场景。
 */
@Component
@ConditionalOnProperty(prefix = "ainer.task.engine", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TaskExecutionEngine implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionEngine.class);

    private final TaskClaimMapper claimMapper;
    private final dev.ainer.module.task.tasks.application.TaskRepository repository;
    private final TaskEngineProperties properties;
    private final java.time.Clock clock;
    private final String instanceId;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final java.util.Map<String, TaskHandler> handlers = new java.util.concurrent.ConcurrentHashMap<>();

    public TaskExecutionEngine(
            TaskClaimMapper claimMapper,
            dev.ainer.module.task.tasks.application.TaskRepository repository,
            TaskEngineProperties properties,
            java.time.Clock clock,
            org.springframework.beans.factory.ObjectProvider<TaskHandler> handlerProvider) {
        this.claimMapper = claimMapper;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.instanceId = "engine-" + UUID.randomUUID().toString().substring(0, 8);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("task-engine-poller").factory());
        handlerProvider.stream().forEach(handler ->
                handlers.put(handler.taskType(), handler));
    }

    /** 产品注册的处理器端口：按 task_type 派发。 */
    public interface TaskHandler {

        /** 此处理器负责的任务类型标识（与 TaskDefinition.task_type 匹配）。 */
        String taskType();

        /** 执行任务；payload 是不可信输入，handler 自行校验。 */
        void execute(String payloadJson) throws Exception;
    }

    @PostConstruct
    void start() {
        running.set(true);
        int swept = sweepZombies();
        if (swept > 0) {
            LOGGER.info("任务引擎启动：重置了 {} 个僵尸 RUNNING 任务", swept);
        }
        scheduler.scheduleWithFixedDelay(this::poll,
                properties.pollIntervalMs(), properties.pollIntervalMs(),
                TimeUnit.MILLISECONDS);
        LOGGER.info("任务引擎已启动：instance={} handlers={} poll={}ms",
                instanceId, handlers.keySet(), properties.pollIntervalMs());
    }

    @PreDestroy
    @Override
    public void close() {
        running.set(false);
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 一次轮询周期：僵尸清扫 → 领取（含同事务审计）→ 派发。 */
    void poll() {
        if (!running.get()) {
            return;
        }
        try {
            sweepZombies();
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            List<TaskJobRow> claimed = claimMapper.claimReadyJobs(
                    instanceId, properties.batchSize(), now);
            for (TaskJobRow row : claimed) {
                executeAsync(toDomain(row), row.getTimeoutSeconds());
            }
        } catch (Exception e) {
            LOGGER.error("任务引擎轮询异常", e);
        }
    }

    /** 僵尸清扫：locked_at 早于「定义 timeout × 倍数」的 RUNNING 重置回 PENDING。 */
    private int sweepZombies() {
        try {
            int reset = repository.resetZombieRunning(
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    properties.zombieCutoffMultiplier());
            if (reset > 0) {
                LOGGER.warn("僵尸 RUNNING 任务重置：{} 个", reset);
            }
            return reset;
        } catch (Exception e) {
            LOGGER.error("僵尸清扫异常", e);
            return 0;
        }
    }

    private void executeAsync(TaskJob job, Integer timeoutSeconds) {
        Thread.ofVirtual().name("task-" + job.id()).start(() -> execute(job, timeoutSeconds));
    }

    private void execute(TaskJob job, Integer timeoutSeconds) {
        TaskHandler handler = handlers.get(job.taskType());
        if (handler == null) {
            completeWithRetry(job, now(), "no handler registered for: " + job.taskType());
            return;
        }
        // 超时看门狗：到期后按失败处理（CAS 迁移）；不杀线程，迟到结果由 CAS 丢弃
        ScheduledFuture<?> watchdog = scheduler.schedule(
                () -> completeWithRetry(job, now(),
                        "timeout after " + timeoutSeconds + "s"),
                timeoutSeconds == null ? 300 : timeoutSeconds, TimeUnit.SECONDS);
        try {
            handler.execute(job.payloadJson());
            boolean cancelled = watchdog.cancel(false);
            if (!cancelled) {
                LOGGER.debug("任务执行完成但已超时迁移，结果丢弃: {} type={}",
                        job.id(), job.taskType());
                return;
            }
            Instant finishedAt = now();
            // 周期任务成功后回到 PENDING 并推进 next_run_at（ADR-0047 §3）；一次性任务终态 SUCCEEDED
            String successStatus = job.periodic() ? "PENDING" : "SUCCEEDED";
            if (repository.completeJob(job.id(), successStatus, null,
                    resetForPeriodic(job), finishedAt)) {
                audit(job, "SUCCEEDED", null, finishedAt);
            }
            LOGGER.debug("任务成功: {} type={}", job.id(), job.taskType());
        } catch (Exception e) {
            watchdog.cancel(false);
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (error.length() > 500) {
                error = error.substring(0, 500);
            }
            completeWithRetry(job, now(), error);
        }
    }

    /**
     * 失败收口：耗尽置 EXHAUSTED（终态），否则指数退避后重试。
     * 全部以 {@code status = 'RUNNING'} 条件更新；返回 0 行说明看门狗与真实结果竞争落败，
     * 本地结果丢弃。
     */
    private void completeWithRetry(TaskJob job, Instant now, String error) {
        if (job.attemptCount() >= job.maxAttempts()) {
            if (repository.completeJob(job.id(), "EXHAUSTED", error, null, now)) {
                audit(job, "EXHAUSTED", error, now);
                LOGGER.warn("任务耗尽: {} type={} attempts={}/{} error={}",
                        job.id(), job.taskType(), job.attemptCount(), job.maxAttempts(), error);
            }
            return;
        }
        // 指数退避：base × 2^(attempt-1)，上限 max
        long backoffMs = Math.min(
                properties.retryBaseMs() * (1L << Math.min(job.attemptCount() - 1, 20)),
                properties.retryMaxMs());
        Instant nextRunAt = now.plus(backoffMs, ChronoUnit.MILLIS);
        if (repository.completeJob(job.id(), "FAILED", error, nextRunAt, now)) {
            String detail = "backoff=" + backoffMs + "ms error=" + error;
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            audit(job, "RETRY_SCHEDULED", detail, now);
            LOGGER.info("任务失败重试: {} type={} attempt={}/{} next={}ms error={}",
                    job.id(), job.taskType(), job.attemptCount(), job.maxAttempts(),
                    backoffMs, error);
        } else {
            LOGGER.debug("失败收口竞争落败，结果丢弃: {}", job.id());
        }
    }

    /** 引擎侧生命周期审计：actor 记录为 system SERVICE + 引擎实例标识。 */
    private void audit(TaskJob job, String event, String detail, Instant at) {
        try {
            repository.insertAudit(new dev.ainer.module.task.tasks.domain.TaskAudit(
                    dev.ainer.core.uuid.Uuidv7.generate(), job.id(), event,
                    job.attemptCount(), "system", "SERVICE", instanceId, detail, at));
        } catch (Exception e) {
            LOGGER.error("任务生命周期审计写入失败: {} event={}", job.id(), event, e);
        }
    }

    /** 周期任务成功后重置 next_run_at；非周期返回 null（终态）。 */
    private java.time.Instant resetForPeriodic(TaskJob job) {
        if (job.periodic()) {
            return now().plus(job.intervalSeconds(), ChronoUnit.SECONDS);
        }
        return null;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static TaskJob toDomain(TaskJobRow row) {
        return new TaskJob(row.getId(), row.getTaskType(), row.getPayloadJson(),
                row.getStatus(), row.getAttemptCount(), row.getMaxAttempts(),
                row.getNextRunAt(), row.getIntervalSeconds(), row.getLockedBy(),
                row.getLockedAt(), row.getLastError(),
                row.getCreatedByIssuer(), row.getCreatedByType(), row.getCreatedById(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getCompletedAt());
    }
}
