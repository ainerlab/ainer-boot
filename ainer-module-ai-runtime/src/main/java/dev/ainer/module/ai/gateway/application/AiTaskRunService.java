package dev.ainer.module.ai.gateway.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.ai.gateway.domain.AiResult;
import dev.ainer.module.ai.gateway.domain.AiTask;
import dev.ainer.module.ai.gateway.domain.AiTaskRun;
import dev.ainer.module.ai.gateway.domain.AiTaskRunStatus;
import dev.ainer.module.ai.gateway.domain.AiTaskStatus;
import dev.ainer.module.ai.gateway.domain.ContextSnapshot;
import dev.ainer.module.ai.gateway.domain.MessageRole;
import dev.ainer.module.ai.gateway.domain.ModelMessage;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 受治理 AI 任务执行服务。见 ADR-0023。
 *
 * <p>把 Task 创建 → Context Snapshot 构建 → AI Gateway 调用 → Result 保存串成完整闭环。
 * 每一步的失败都更新 Task/Run 状态为 FAILED 并抛出异常。
 *
 * <p><b>边界</b>：Phase 1（创建 Task + Snapshot + Run + 翻状态）用一个显式事务提交，
 * 中途失败不会留下「RUNNING 的 task 却没有对应 run」的半成品；Phase 2 不包事务，
 * 让 Gateway 的 REQUIRES_NEW 审计写在自己的事务里。状态回写一律带期望态（CAS），
 * 迟到的写回不会覆盖自愈已经写入的终态。
 */
@Service
public class AiTaskRunService {

    private static final Logger LOG = LoggerFactory.getLogger(AiTaskRunService.class);

    private final AiTaskRepository taskRepository;
    private final AiGatewayApplicationService gatewayService;
    private final GovernedAiExecutionContextResolver contextResolver;
    private final ContextSnapshotBuilder snapshotBuilder;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AiTaskRunService(
            AiTaskRepository taskRepository,
            AiGatewayApplicationService gatewayService,
            GovernedAiExecutionContextResolver contextResolver,
            ContextSnapshotBuilder snapshotBuilder,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.gatewayService = gatewayService;
        this.contextResolver = contextResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AiTaskRunResult executeTask(AiTaskCreateCommand command, AuthenticatedPrincipal principal) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(principal, "principal");
        String requestId = dev.ainer.core.uuid.Uuidv7.generate().toString();

        // Phase 1: 创建 Task + Snapshot + TaskRun（独立事务提交，确保后续 Gateway 可见）
        TaskRunCreated created = createTaskAndRun(command, principal, requestId);

        // Phase 2: 调用 AI Gateway（无外层事务包裹，audit 的 REQUIRES_NEW 可见已提交行）
        try {
            ChatCompletionCommand gatewayCommand = buildGatewayCommand(
                    created.governedCtx(), command, created.snapshot());
            CompletionResult completion = gatewayService.complete(gatewayCommand);

            // Phase 3: 保存 Result + 完成（独立事务）
            AiTaskRunResult result = completeTaskRun(created, completion);
            return result;
        } catch (RuntimeException failure) {
            failTaskRun(created);
            throw failure;
        }
    }

    public TaskRunCreated createTaskAndRun(AiTaskCreateCommand command,
                                            AuthenticatedPrincipal principal, String requestId) {
        // 用一个显式事务包住 Phase 1：Task 翻 RUNNING 与插入 TaskRun 必须同生共死，
        // 否则中途失败会留下「RUNNING 的 task 没有任何 run」的中间态（自愈虽然能兜底，
        // 但正常路径不该制造它）。
        return transactionTemplate.execute(status -> createTaskAndRunWithinTransaction(command, principal, requestId));
    }

    private TaskRunCreated createTaskAndRunWithinTransaction(AiTaskCreateCommand command,
                                                             AuthenticatedPrincipal principal, String requestId) {
        GovernedAiExecutionContext governedCtx = contextResolver.resolve(principal, requestId);
        governedCtx = GovernedAiExecutionContextBuilder.from(governedCtx)
                .purpose(command.purpose())
                .taskType(command.taskType())
                .build();

        Instant now = clock.instant();
        AiTask task = new AiTask(
                dev.ainer.core.uuid.Uuidv7.generate(),
                governedCtx.workspaceId(),
                command.taskType(),
                command.targetIdentityId(),
                AiTaskStatus.PENDING,
                command.trigger(),
                principal.subjectId(),
                governedCtx.entitlementPolicyVersion(),
                now,
                now);
        taskRepository.insertTask(task);

        ContextSnapshotBuilder.ContextSnapshotData snapshotData =
                snapshotBuilder.build(task, governedCtx);
        ContextSnapshot snapshot = new ContextSnapshot(
                dev.ainer.core.uuid.Uuidv7.generate(),
                snapshotData.identityId(),
                snapshotData.identityVersionId(),
                snapshotData.evidenceRefsJson(),
                snapshotData.memoryRefsJson(),
                now,
                1,
                now);
        taskRepository.insertContextSnapshot(snapshot);

        if (!taskRepository.updateTaskStatus(task.id(), AiTaskStatus.PENDING, AiTaskStatus.RUNNING, now)) {
            throw new BusinessException(StandardErrorCode.INTERNAL_ERROR);
        }

        AiTaskRun run = new AiTaskRun(
                dev.ainer.core.uuid.Uuidv7.generate(),
                task.id(),
                snapshot.id(),
                serializeContext(governedCtx),
                AiTaskRunStatus.RUNNING,
                now,
                null);
        taskRepository.insertTaskRun(run);

        return new TaskRunCreated(task, run, snapshot, governedCtx);
    }

    public AiTaskRunResult completeTaskRun(TaskRunCreated created, CompletionResult completion) {
        // 注释里的「独立事务」此前并不存在：Result 落库与 Run/Task 置 COMPLETED 必须原子，
        // 否则会留下「有 Result 但 Run 仍是 RUNNING」的中间态（只能靠自愈兜底）。
        return transactionTemplate.execute(status -> completeTaskRunWithinTransaction(created, completion));
    }

    private AiTaskRunResult completeTaskRunWithinTransaction(TaskRunCreated created, CompletionResult completion) {
        Instant completedAt = clock.instant();
        AiResult result = new AiResult(
                dev.ainer.core.uuid.Uuidv7.generate(),
                created.run().id(),
                completion.invocationId(),
                completion.completion().content(),
                "[]",
                "[]",
                1,
                completedAt);
        taskRepository.insertResult(result);
        if (!taskRepository.updateTaskRunStatus(
                created.run().id(), AiTaskRunStatus.RUNNING, AiTaskRunStatus.COMPLETED, completedAt)) {
            // 自愈或别的终结路径已经写过终态：不再覆盖，只记录，避免把已完成的事实改成别的状态。
            LOG.warn("AI task run {} was already terminal before the completion write-back", created.run().id());
        }
        if (!taskRepository.updateTaskStatus(
                created.task().id(), AiTaskStatus.RUNNING, AiTaskStatus.COMPLETED, completedAt)) {
            LOG.warn("AI task {} was already terminal before the completion write-back", created.task().id());
        }
        return new AiTaskRunResult(created.task().id(), created.run().id(), result.id(), completion);
    }

    public void failTaskRun(TaskRunCreated created) {
        Instant failedAt = clock.instant();
        if (!taskRepository.updateTaskRunStatus(
                created.run().id(), AiTaskRunStatus.RUNNING, AiTaskRunStatus.FAILED, failedAt)) {
            LOG.warn("AI task run {} was already terminal before the failure write-back", created.run().id());
        }
        if (!taskRepository.updateTaskStatus(
                created.task().id(), AiTaskStatus.RUNNING, AiTaskStatus.FAILED, failedAt)) {
            LOG.warn("AI task {} was already terminal before the failure write-back", created.task().id());
        }
    }

    private ChatCompletionCommand buildGatewayCommand(
            GovernedAiExecutionContext ctx,
            AiTaskCreateCommand command,
            ContextSnapshot snapshot) {
        List<ModelMessage> messages = List.of(
                new ModelMessage(MessageRole.SYSTEM, command.systemPrompt()),
                new ModelMessage(MessageRole.USER, command.userPrompt()));
        InvocationContext invocationCtx = new InvocationContext(
                ctx.actorId(),
                ctx.requestId());
        return new ChatCompletionCommand(
                invocationCtx,
                command.model(),
                messages,
                command.maxOutputTokens(),
                new java.math.BigDecimal("0.7"));
    }

    private static String serializeContext(GovernedAiExecutionContext ctx) {
        return """
                {"actorType":"%s","actorId":"%s","requestId":"%s",\
                "purpose":"%s","taskType":"%s"}""".formatted(
                ctx.actorType(), ctx.actorId(),
                ctx.requestId(),
                ctx.purpose() != null ? ctx.purpose() : "",
                ctx.taskType() != null ? ctx.taskType() : "");
    }

    public record AiTaskCreateCommand(
            String taskType,
            UUID targetIdentityId,
            String purpose,
            String trigger,
            String model,
            String systemPrompt,
            String userPrompt,
            int maxOutputTokens) {

        public AiTaskCreateCommand {
            Objects.requireNonNull(taskType, "taskType");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(systemPrompt, "systemPrompt");
            Objects.requireNonNull(userPrompt, "userPrompt");
            if (trigger == null || trigger.isBlank()) {
                trigger = "manual";
            }
            if (maxOutputTokens <= 0) {
                maxOutputTokens = 4096;
            }
        }
    }

    public record AiTaskRunResult(
            UUID taskId,
            UUID taskRunId,
            UUID resultId,
            CompletionResult completion) {

        public AiTaskRunResult {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(taskRunId, "taskRunId");
            Objects.requireNonNull(resultId, "resultId");
            Objects.requireNonNull(completion, "completion");
        }
    }

    public record TaskRunCreated(
            AiTask task,
            AiTaskRun run,
            ContextSnapshot snapshot,
            GovernedAiExecutionContext governedCtx) {

        public TaskRunCreated {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(governedCtx, "governedCtx");
        }
    }
}
