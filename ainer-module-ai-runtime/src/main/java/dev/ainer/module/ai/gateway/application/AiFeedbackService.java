package dev.ainer.module.ai.gateway.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.ai.gateway.domain.AiFeedback;
import dev.ainer.module.ai.gateway.domain.AiFeedbackDecision;
import dev.ainer.module.ai.gateway.domain.AiResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 人工反馈服务。见 ADR-0023。
 *
 * <p>记录对 AI Result 的 ACCEPT / EDIT / REJECT 决策，支持修正内容和 Memory Proposal。
 * 反馈进入闭环后，后续 AI 任务可以引用已批准的 Memory。
 */
@Service
public class AiFeedbackService {

    private final AiTaskRepository taskRepository;
    private final Clock clock;

    public AiFeedbackService(AiTaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public AiFeedback submitFeedback(FeedbackCommand command, String reviewerId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(reviewerId, "reviewerId");

        AiResult result = taskRepository.findResultByRun(command.runId())
                .orElseThrow(() -> new BusinessException(StandardErrorCode.NOT_FOUND));
        AiFeedbackDecision decision = AiFeedbackDecision.valueOf(command.decision().toUpperCase());

        if (decision == AiFeedbackDecision.EDIT && (command.editedContent() == null || command.editedContent().isBlank())) {
            throw new BusinessException(StandardErrorCode.INVALID_REQUEST);
        }

        Instant now = clock.instant();
        AiFeedback feedback = new AiFeedback(
                dev.ainer.core.uuid.Uuidv7.generate(),
                result.id(),
                decision,
                decision == AiFeedbackDecision.EDIT ? command.editedContent() : null,
                command.feedbackReason(),
                command.memoryProposalJson() != null ? command.memoryProposalJson() : "[]",
                reviewerId,
                now);
        taskRepository.insertFeedback(feedback);
        return feedback;
    }

    public record FeedbackCommand(
            UUID runId,
            String decision,
            String editedContent,
            String feedbackReason,
            String memoryProposalJson) {}
}
