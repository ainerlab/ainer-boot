package dev.ainer.module.ai.gateway.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 受治理 AI 执行上下文：把身份、租户、工作空间、权益、数据范围和追溯标识统一为一个不可变契约。
 *
 * <p>这是 AI 调用链的统一入口上下文。调用链为：
 * <pre>
 * 身份验证 → 上下文解析 → AI Task 权限判断 → Entitlement 与策略解析
 * → 预算预占 → Context Snapshot 构建 → 模型调用 → 安全检查与结果保存
 * → 实际用量结算 → 审计、反馈与领域事件
 * </pre>
 *
 * <p>当前字段分为三层：
 * <ul>
 *   <li>已落地：actorType, actorId, requestId, traceId, scopes</li>
 *   <li>待接入：workspaceId, memberId（待 Workspace 域模型对接后填充）</li>
 *   <li>规划中：identityId, identityVersionId, purpose, taskType, dataScope,
 *       dataClassification, entitlementPolicyVersion, retentionPolicy（待领域模型落地后填充）</li>
 * </ul>
 * 所有"待接入"和"规划中"字段为 nullable，Resolver 只填当前可解析的字段。
 */
public record GovernedAiExecutionContext(
        UUID workspaceId,
        String actorType,
        String actorId,
        UUID memberId,
        UUID identityId,
        UUID identityVersionId,
        String purpose,
        String taskType,
        Set<String> scopes,
        String dataScope,
        String dataClassification,
        String entitlementPolicyVersion,
        String retentionPolicy,
        String traceId,
        String requestId) {

    public GovernedAiExecutionContext {
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(requestId, "requestId");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean isUser() {
        return "USER".equals(actorType);
    }

    public boolean isService() {
        return "SERVICE".equals(actorType);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
