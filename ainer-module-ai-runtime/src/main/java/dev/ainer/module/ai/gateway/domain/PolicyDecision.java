package dev.ainer.module.ai.gateway.domain;

/**
 * 网关策略决策结果：放行，或按拒绝原因分类（模型、prompt 大小、敏感数据、限流、预算）。
 */
public enum PolicyDecision {
    ALLOWED,
    REJECTED_MODEL,
    REJECTED_PROMPT_SIZE,
    REJECTED_SENSITIVE_DATA,
    REJECTED_RATE_LIMIT,
    REJECTED_BUDGET
}
