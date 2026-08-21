package dev.ainer.module.task.tasks.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.task.tasks.domain.TaskAudit;
import dev.ainer.module.task.tasks.domain.TaskDefinition;
import dev.ainer.module.task.tasks.domain.TaskJob;

import java.util.UUID;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 任务管理应用服务（ADR-0047）：注册任务类型、提交作业（延迟/周期）、管理面操作（取消/
 * 重试/启停）。scope 在服务内对已验证 principal 强制；同事务写 append-only 审计。
 *
 * <p>执行引擎（{@code TaskExecutionEngine}）独立轮询领取与执行；本服务只管生命周期写入。
 */
@Service
public class TaskApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern SAFE_TASK_TYPE = Pattern.compile("[a-z][a-z0-9-]{2,127}");

    private final TaskRepository repository;
    private final Clock clock;

    public TaskApplicationService(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 定义

    @Transactional
    public TaskDefinition registerDefinition(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            String taskType, String displayName, String handlerRef,
            int maxAttempts, int timeoutSeconds) {
        requireManage(principal);
        String normalizedType = taskType == null ? "" : taskType.strip().toLowerCase();
        if (!SAFE_TASK_TYPE.matcher(normalizedType).matches()) {
            throw new BusinessException(TaskErrorCode.INVALID_TASK_TYPE);
        }
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 256) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
        if (handlerRef == null || handlerRef.isBlank() || handlerRef.length() > 256) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
        if (maxAttempts < 1 || maxAttempts > 20 || timeoutSeconds < 1 || timeoutSeconds > 86400) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }
        Instant now = micros(clock.instant());
        TaskDefinition definition = new TaskDefinition(
                Uuidv7.generate(), normalizedType, displayName.strip(), handlerRef.strip(),
                maxAttempts, timeoutSeconds, "ACTIVE", now, now);
        try {
            repository.insertDefinition(definition);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new BusinessException(TaskErrorCode.DUPLICATE_TASK_TYPE);
        }
        audit(null, "PAUSED".equals(definition.status()) ? "PAUSED" : "SUBMITTED",
                null, principal, now, "definition registered: " + normalizedType);
        return definition;
    }

    @Transactional(readOnly = true)
    public List<TaskDefinition> pageDefinitions(
            AuthenticatedPrincipal principal, long page, long size) {
        requireRead(principal);
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(TaskErrorCode.INVALID_PAGE);
        }
        return repository.pageDefinitions((page - 1) * (int) size, (int) size);
    }

    @Transactional(readOnly = true)
    public long countDefinitions(AuthenticatedPrincipal principal) {
        requireRead(principal);
        return repository.countDefinitions();
    }

    @Transactional
    public TaskDefinition changeDefinitionStatus(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            String taskType, boolean pause) {
        requireManage(principal);
        TaskDefinition definition = repository.findDefinitionByType(taskType)
                .orElseThrow(() -> new BusinessException(TaskErrorCode.DEFINITION_NOT_FOUND));
        String newStatus = pause ? "PAUSED" : "ACTIVE";
        if (newStatus.equals(definition.status())) {
            return definition;
        }
        Instant now = micros(clock.instant());
        if (!repository.updateDefinitionStatus(taskType, newStatus, now)) {
            throw new BusinessException(TaskErrorCode.DEFINITION_NOT_FOUND);
        }
        audit(null, pause ? "PAUSED" : "RESUMED", null, principal, now, taskType);
        return repository.findDefinitionByType(taskType).orElseThrow();
    }

    // ------------------------------------------------------------------ 作业

    /**
     * 提交任务：延迟执行（delaySeconds 后）或周期执行（intervalSeconds 非空）。
     */
    @Transactional
    public TaskJob submitJob(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            String taskType, String payloadJson,
            @Nullable Long delaySeconds, @Nullable Long intervalSeconds) {
        requireScope(principal, TaskAuthorities.SUBMIT);
        TaskDefinition definition = repository.findDefinitionByType(taskType)
                .orElseThrow(() -> new BusinessException(TaskErrorCode.DEFINITION_NOT_FOUND));
        if (!definition.active()) {
            throw new BusinessException(TaskErrorCode.DEFINITION_PAUSED);
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            payloadJson = "{}";
        }
        payloadJson = payloadJson.strip();
        if (!payloadJson.startsWith("{") || !payloadJson.endsWith("}")) {
            throw new BusinessException(TaskErrorCode.INVALID_PAYLOAD);
        }
        if (payloadJson.length() > 65536) {
            throw new BusinessException(TaskErrorCode.INVALID_PAYLOAD);
        }
        long delay = delaySeconds == null ? 0 : delaySeconds;
        if (delay < 0) {
            throw new BusinessException(TaskErrorCode.INVALID_INTERVAL);
        }
        if (intervalSeconds != null && intervalSeconds <= 0) {
            throw new BusinessException(TaskErrorCode.INVALID_INTERVAL);
        }
        Instant now = micros(clock.instant());
        TaskJob job = new TaskJob(
                Uuidv7.generate(), taskType, payloadJson, "PENDING",
                0, definition.maxAttempts(),
                now.plus(delay, ChronoUnit.SECONDS), intervalSeconds,
                null, null, null,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(),
                now, now, null);
        repository.insertJob(job);
        audit(job.id(), "SUBMITTED", 0, principal, now,
                "delay=" + delay + "s interval=" + intervalSeconds + "s");
        return job;
    }

    @Transactional(readOnly = true)
    public TaskJob getJob(AuthenticatedPrincipal principal, UUID id) {
        requireRead(principal);
        return repository.findJob(id)
                .orElseThrow(() -> new BusinessException(TaskErrorCode.JOB_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<TaskJob> pageJobs(
            AuthenticatedPrincipal principal, @Nullable String status,
            @Nullable String taskType, long page, long size) {
        requireRead(principal);
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(TaskErrorCode.INVALID_PAGE);
        }
        return repository.pageJobs(status, taskType, (page - 1) * (int) size, (int) size);
    }

    @Transactional(readOnly = true)
    public long countJobs(AuthenticatedPrincipal principal, @Nullable String status,
            @Nullable String taskType) {
        requireRead(principal);
        return repository.countJobs(status, taskType);
    }

    @Transactional
    public TaskJob cancelJob(AuthenticatedPrincipal principal, @Nullable String requestId, UUID id) {
        requireManage(principal);
        TaskJob job = repository.findJob(id)
                .orElseThrow(() -> new BusinessException(TaskErrorCode.JOB_NOT_FOUND));
        if (job.terminal() || "RUNNING".equals(job.status())) {
            throw new BusinessException(TaskErrorCode.JOB_NOT_CANCELLABLE);
        }
        Instant now = micros(clock.instant());
        if (!repository.cancelJob(id, now)) {
            throw new BusinessException(TaskErrorCode.JOB_NOT_CANCELLABLE);
        }
        audit(id, "CANCELLED", job.attemptCount(), principal, now, null);
        return repository.findJob(id).orElseThrow();
    }

    @Transactional
    public TaskJob retryJob(AuthenticatedPrincipal principal, @Nullable String requestId, UUID id) {
        requireManage(principal);
        TaskJob job = repository.findJob(id)
                .orElseThrow(() -> new BusinessException(TaskErrorCode.JOB_NOT_FOUND));
        if (!"FAILED".equals(job.status()) && !"EXHAUSTED".equals(job.status())) {
            throw new BusinessException(TaskErrorCode.JOB_NOT_RETRYABLE);
        }
        Instant now = micros(clock.instant());
        if (!repository.retryJob(id, now, now)) {
            throw new BusinessException(TaskErrorCode.JOB_NOT_RETRYABLE);
        }
        audit(id, "RETRY_SCHEDULED", job.attemptCount(), principal, now, "manual retry");
        return repository.findJob(id).orElseThrow();
    }

    // ------------------------------------------------------------------ 辅助

    private void audit(@Nullable UUID jobId, String event, @Nullable Integer attempt,
            AuthenticatedPrincipal principal, Instant at, @Nullable String detail) {
        repository.insertAudit(new TaskAudit(
                Uuidv7.generate(), jobId, event, attempt,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(), detail, at));
    }

    private static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireManage(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(TaskAuthorities.MANAGE)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireRead(AuthenticatedPrincipal principal) {
        if (!principal.hasScope(TaskAuthorities.READ)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireScope(AuthenticatedPrincipal principal, String scope) {
        if (!principal.hasScope(scope)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }
}
