package dev.ainer.module.ai.agent.api;

import dev.ainer.module.ai.agent.application.AiAgentPage;

import java.util.List;

/** Agent 定义的分页信封。 */
public record AgentPageResponse(List<AgentResponse> items, int page, int size, long total) {

    public static AgentPageResponse from(AiAgentPage page) {
        return new AgentPageResponse(
                page.items().stream().map(AgentResponse::from).toList(),
                page.page(),
                page.size(),
                page.total());
    }
}
