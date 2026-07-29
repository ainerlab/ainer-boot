package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.AiTask;
import org.springframework.stereotype.Component;

/**
 * 默认 stub 实现：返回空 evidence_refs 和 memory_refs。
 *
 * <p>当产品层未提供领域特化的 {@link ContextSnapshotBuilder} 时使用此实现。
 * 产品层可注册 {@code @Primary @Component} 的 {@link ContextSnapshotBuilder} 替换此默认实现。
 */
@Component
public class DefaultContextSnapshotBuilder implements ContextSnapshotBuilder {

    @Override
    public ContextSnapshotData build(AiTask task, GovernedAiExecutionContext governedCtx) {
        return new ContextSnapshotData(
                task.targetIdentityId(),
                null,
                "[]",
                "[]");
    }
}
