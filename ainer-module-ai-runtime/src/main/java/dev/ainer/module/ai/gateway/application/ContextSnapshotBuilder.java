package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiTask;

import java.util.UUID;

/**
 * 领域特化的上下文快照构建器。
 *
 * <p>每个业务场景（如 Identity 周报）提供自己的实现，从 Identity、Publication、Metrics、
 * Feedback、Memory 等领域事实构建不可变快照。ainer-boot 内核只定义接口契约，
 * 不提供具体领域数据查询。
 */
@FunctionalInterface
public interface ContextSnapshotBuilder {

    /**
     * 为指定 Task 和治理上下文构建快照数据。
     *
     * @param task          业务任务
     * @param governedCtx   治理上下文
     * @return 快照数据（identity、evidence_refs JSON、memory_refs JSON）
     */
    ContextSnapshotData build(AiTask task, GovernedAiExecutionContext governedCtx);

    record ContextSnapshotData(
            UUID identityId,
            UUID identityVersionId,
            String evidenceRefsJson,
            String memoryRefsJson) {

        public ContextSnapshotData {
            if (evidenceRefsJson == null || evidenceRefsJson.isBlank()) {
                evidenceRefsJson = "[]";
            }
            if (memoryRefsJson == null || memoryRefsJson.isBlank()) {
                memoryRefsJson = "[]";
            }
        }
    }
}
