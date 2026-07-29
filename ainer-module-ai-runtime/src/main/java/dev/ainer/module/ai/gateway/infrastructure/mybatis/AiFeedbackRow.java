package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

public class AiFeedbackRow {
    private UUID id;
    private UUID resultId;
    private String decision;
    private String editedContent;
    private String feedbackReason;
    private String memoryProposal;
    private String reviewerId;
    private Instant reviewedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getResultId() { return resultId; }
    public void setResultId(UUID v) { this.resultId = v; }
    public String getDecision() { return decision; }
    public void setDecision(String v) { this.decision = v; }
    public String getEditedContent() { return editedContent; }
    public void setEditedContent(String v) { this.editedContent = v; }
    public String getFeedbackReason() { return feedbackReason; }
    public void setFeedbackReason(String v) { this.feedbackReason = v; }
    public String getMemoryProposal() { return memoryProposal; }
    public void setMemoryProposal(String v) { this.memoryProposal = v; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String v) { this.reviewerId = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
}
