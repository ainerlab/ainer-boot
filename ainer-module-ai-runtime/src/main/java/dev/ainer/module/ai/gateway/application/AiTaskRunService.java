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
import dev.ainer.security.actor.AuthenticatedActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class AiTaskRunService {

    private final AiTaskRepository taskRepository;
    private final AiGatewayApplicationService gatewayService;
    private final GovernedAiExecutionContextResolver contextResolver;
    private final ContextSnapshotBuilder snapshotBuilder;
    private final Clock clock;

    public AiTaskRunService(
            AiTaskRepository taskRepository,
            AiGatewayApplicationService gatewayService,
            GovernedAiExecutionContextResolver contextResolver,
            ContextSnapshotBuilder snapshotBuilder,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.gatewayService = gatewayService;
        this.contextResolver = contextResolver;
        this.snapshotBuilder = snapshotBuilder;
        this.clock = clock;
    }

    @Transactional
    public AiTaskRunResult executeTask(AiTaskCreateCommand command, AuthenticatedActor actor) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
        String requestId = UUID.randomUUID().toString();

        // 1. 解析治理上下文
        GovernedAiExecutionContext governedCtx = contextResolver.resolve(actor, requestId);
        governedCtx = GovernedAiExecutionContextBuilder.from(governedCtx)
                .purpose(command.purpose())
                .taskType(command.taskType())
                .build();

        // 2. 创建 Task（PENDING）
        Instant now = clock.instant();
        AiTask task = new AiTask(
                UUID.randomUUID(),
                UUID.fromString(actor.tenantId()),
                governedCtx.workspaceId(),
                command.taskType(),
                command.targetIdentityId(),
                AiTaskStatus.PENDING,
                command.trigger(),
                actor.subjectId(),
                governedCtx.entitlementPolicyVersion(),
                now,
                now);
        taskRepository.insertTask(task);

        // 3. 构建 Context Snapshot
        ContextSnapshotBuilder.ContextSnapshotData snapshotData =
                snapshotBuilder.build(task, governedCtx);
        ContextSnapshot snapshot = new ContextSnapshot(
                UUID.randomUUID(),
                task.tenantId(),
                snapshotData.identityId(),
                snapshotData.identityVersionId(),
                snapshotData.evidenceRefsJson(),
                snapshotData.memoryRefsJson(),
                now,
                1,
                now);
        taskRepository.insertContextSnapshot(snapshot);

        // 4. Task → RUNNING
        if (!taskRepository.updateTaskStatus(task.id(), AiTaskStatus.PENDING, AiTaskStatus.RUNNING, now)) {
            throw new BusinessException(StandardErrorCode.INTERNAL_ERROR);
        }

        // 5. 创建 TaskRun（RUNNING）
        AiTaskRun run = new AiTaskRun(
                UUID.randomUUID(),
                task.id(),
                snapshot.id(),
                serializeContext(governedCtx),
                AiTaskRunStatus.RUNNING,
                now,
                null);
        taskRepository.insertTaskRun(run);

        // 6. 调用 AI Gateway
        try {
            ChatCompletionCommand gatewayCommand = buildGatewayCommand(
                    governedCtx, command, snapshot);
            CompletionResult completion = gatewayService.complete(gatewayCommand);

            // 7. 保存 Result
            Instant completedAt = clock.instant();
            AiResult result = new AiResult(
                    UUID.randomUUID(),
                    run.id(),
                    completion.invocationId(),
                    completion.completion().content(),
                    "[]",
                    "[]",
                    1,
                    completedAt);
            taskRepository.insertResult(result);

            // 8. 完成
            taskRepository.updateTaskRunStatus(run.id(), AiTaskRunStatus.COMPLETED.name(), completedAt);
            taskRepository.updateTaskStatus(task.id(), AiTaskStatus.RUNNING, AiTaskStatus.COMPLETED, completedAt);

            return new AiTaskRunResult(task.id(), run.id(), result.id(), completion);
        } catch (RuntimeException failure) {
            Instant failedAt = clock.instant();
            taskRepository.updateTaskRunStatus(run.id(), AiTaskRunStatus.FAILED.name(), failedAt);
            taskRepository.updateTaskStatus(task.id(), AiTaskStatus.RUNNING, AiTaskStatus.FAILED, failedAt);
            throw failure;
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
                ctx.tenantId().toString(),
                ctx.actorId(),
                ctx.requestId());
        return new ChatCompletionCommand(
                invocationCtx,
                command.model(),
                messages,
                command.maxOutputTokens(),
                null);
    }

    private static String serializeContext(GovernedAiExecutionContext ctx) {
        return """
                {"tenantId":"%s","actorType":"%s","actorId":"%s","requestId":"%s",\
                "purpose":"%s","taskType":"%s"}""".formatted(
                ctx.tenantId(), ctx.actorType(), ctx.actorId(),
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
}
