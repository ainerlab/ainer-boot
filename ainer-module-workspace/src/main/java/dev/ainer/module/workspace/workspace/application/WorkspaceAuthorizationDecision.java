package dev.ainer.module.workspace.workspace.application;

/**
 * Workspace 授权检查的审计决策结果：{@code ALLOWED} 或 {@code DENIED}。
 *
 * <p>与授权模块的 ALLOW/DENY/CHALLENGE 三态不同，Workspace 审计只记录检查是否放行，
 * 高风险场景的强认证要求由授权模块负责。
 */
public enum WorkspaceAuthorizationDecision {
    ALLOWED,
    DENIED
}
