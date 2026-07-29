package dev.ainer.module.ai.gateway.infrastructure.mybatis;

import dev.ainer.module.ai.gateway.application.AiTaskRepository;
import dev.ainer.module.ai.gateway.domain.AiFeedback;
import dev.ainer.module.ai.gateway.domain.AiFeedbackDecision;
import dev.ainer.module.ai.gateway.domain.AiResult;
import dev.ainer.module.ai.gateway.domain.AiTask;
import dev.ainer.module.ai.gateway.domain.AiTaskRun;
import dev.ainer.module.ai.gateway.domain.AiTaskRunStatus;
import dev.ainer.module.ai.gateway.domain.AiTaskStatus;
import dev.ainer.module.ai.gateway.domain.ContextSnapshot;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisAiTaskRepository implements AiTaskRepository {

    private final AiTaskMapper mapper;

    public MybatisAiTaskRepository(AiTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertTask(AiTask task) {
        AiTaskRow row = new AiTaskRow();
        row.setId(task.id());
        row.setTenantId(task.tenantId());
        row.setWorkspaceId(task.workspaceId());
        row.setTaskType(task.taskType());
        row.setTargetIdentityId(task.targetIdentityId());
        row.setStatus(task.status().name());
        row.setTrigger(task.trigger());
        row.setTriggeredBy(task.triggeredBy());
        row.setPolicyVersion(task.policyVersion());
        row.setCreatedAt(task.createdAt());
        row.setUpdatedAt(task.updatedAt());
        mapper.insertTask(row);
    }

    @Override
    public Optional<AiTask> findTask(UUID id) {
        return Optional.ofNullable(mapper.selectTaskById(id)).map(this::toTask);
    }

    @Override
    public boolean updateTaskStatus(UUID id, AiTaskStatus expected, AiTaskStatus next, Instant updatedAt) {
        return mapper.updateTaskStatus(id, expected.name(), next.name(), updatedAt) == 1;
    }

    @Override
    public void insertContextSnapshot(ContextSnapshot snapshot) {
        AiContextSnapshotRow row = new AiContextSnapshotRow();
        row.setId(snapshot.id());
        row.setTenantId(snapshot.tenantId());
        row.setIdentityId(snapshot.identityId());
        row.setIdentityVersionId(snapshot.identityVersionId());
        row.setEvidenceRefs(snapshot.evidenceRefsJson());
        row.setMemoryRefs(snapshot.memoryRefsJson());
        row.setAsOf(snapshot.asOf());
        row.setSchemaVersion(snapshot.schemaVersion());
        row.setCreatedAt(snapshot.createdAt());
        mapper.insertContextSnapshot(row);
    }

    @Override
    public void insertTaskRun(AiTaskRun run) {
        AiTaskRunRow row = new AiTaskRunRow();
        row.setId(run.id());
        row.setTaskId(run.taskId());
        row.setContextSnapshotId(run.contextSnapshotId());
        row.setGovernedContext(run.governedContextJson());
        row.setStatus(run.status().name());
        row.setStartedAt(run.startedAt());
        row.setCompletedAt(run.completedAt());
        mapper.insertTaskRun(row);
    }

    @Override
    public boolean updateTaskRunStatus(UUID id, String status, Instant completedAt) {
        return mapper.updateTaskRunStatus(id, status, completedAt) == 1;
    }

    @Override
    public Optional<AiTaskRun> findTaskRun(UUID id) {
        return Optional.ofNullable(mapper.selectTaskRunById(id)).map(this::toTaskRun);
    }

    @Override
    public void insertResult(AiResult result) {
        AiResultRow row = new AiResultRow();
        row.setId(result.id());
        row.setRunId(result.runId());
        row.setInvocationId(result.invocationId());
        row.setContent(result.content());
        row.setFactRefs(result.factRefsJson());
        row.setInferences(result.inferencesJson());
        row.setResultSchemaVersion(result.resultSchemaVersion());
        row.setCreatedAt(result.createdAt());
        mapper.insertResult(row);
    }

    @Override
    public Optional<AiResult> findResultByRun(UUID runId) {
        return Optional.ofNullable(mapper.selectResultByRunId(runId)).map(this::toResult);
    }

    @Override
    public void insertFeedback(AiFeedback feedback) {
        AiFeedbackRow row = new AiFeedbackRow();
        row.setId(feedback.id());
        row.setResultId(feedback.resultId());
        row.setDecision(feedback.decision().name());
        row.setEditedContent(feedback.editedContent());
        row.setFeedbackReason(feedback.feedbackReason());
        row.setMemoryProposal(feedback.memoryProposalJson());
        row.setReviewerId(feedback.reviewerId());
        row.setReviewedAt(feedback.reviewedAt());
        mapper.insertFeedback(row);
    }

    private AiTask toTask(AiTaskRow row) {
        return new AiTask(
                row.getId(), row.getTenantId(), row.getWorkspaceId(),
                row.getTaskType(), row.getTargetIdentityId(),
                AiTaskStatus.valueOf(row.getStatus()),
                row.getTrigger(), row.getTriggeredBy(),
                row.getPolicyVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private AiTaskRun toTaskRun(AiTaskRunRow row) {
        return new AiTaskRun(
                row.getId(), row.getTaskId(), row.getContextSnapshotId(),
                row.getGovernedContext(),
                AiTaskRunStatus.valueOf(row.getStatus()),
                row.getStartedAt(), row.getCompletedAt());
    }

    private AiResult toResult(AiResultRow row) {
        return new AiResult(
                row.getId(), row.getRunId(), row.getInvocationId(),
                row.getContent(), row.getFactRefs(), row.getInferences(),
                row.getResultSchemaVersion(), row.getCreatedAt());
    }
}
