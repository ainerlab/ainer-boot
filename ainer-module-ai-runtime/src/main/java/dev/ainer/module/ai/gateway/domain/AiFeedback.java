package dev.ainer.module.ai.gateway.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AiFeedback(
        UUID id,
        UUID resultId,
        AiFeedbackDecision decision,
        String editedContent,
        String feedbackReason,
        String memoryProposalJson,
        String reviewerId,
        Instant reviewedAt) {

    public AiFeedback {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(memoryProposalJson, "memoryProposalJson");
        Objects.requireNonNull(reviewerId, "reviewerId");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }
}
