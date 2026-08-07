package dev.ainer.module.ai.gateway.application;

import dev.ainer.security.token.AuthenticatedPrincipal;

/**
 * 从已验证主体解析 {@link GovernedAiExecutionContext}。
 *
 * <p>默认实现只填充当前已有的身份字段（actorType, actorId, scopes）。
 * 当 Workspace、Identity Version、Entitlement 等领域模型落地后，
 * 扩展或替换此接口的实现以填充更多字段。
 */
@FunctionalInterface
public interface GovernedAiExecutionContextResolver {

    /**
     * 从已验证主体和请求标识解析治理上下文。
     *
     * @param actor     已验证主体（来自 JWT）
     * @param requestId 请求追踪标识
     * @return 填充了当前可解析字段的治理上下文
     */
    GovernedAiExecutionContext resolve(AuthenticatedPrincipal principal, String requestId);
}
