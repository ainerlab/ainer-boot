package dev.ainer.module.ai.agent.application;

/**
 * Agent 注册表的 scope 常量（ADR-0043）。通过 {@code AuthenticatedPrincipal.hasScope(...)}
 * 命令式检查。
 */
public final class AiAgentAuthorities {

    public static final String MANAGE = "ai.agents.manage";

    private AiAgentAuthorities() {
    }
}
