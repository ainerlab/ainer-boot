package dev.ainer.authorization.policy;

import java.util.UUID;

/**
 * 产品提供的 Agent 定义状态源（ADR-0043 A1）。授权模块绝不依赖 AI 运行时实现；
 * 默认 bean 为 fail-closed（UNKNOWN 即拒绝）。
 */
public interface AgentDefinitionStatusResolver {

    enum AgentStatus {
        ACTIVE,
        RETIRED,
        UNKNOWN
    }

    /** 决策时求值的 Agent 定义当前状态。 */
    AgentStatus agentStatus(UUID agentId);
}
