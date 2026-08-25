package dev.ainer.module.ai.agent.application;

import dev.ainer.module.ai.agent.domain.AiAgentDefinition;

import java.util.List;

/** Agent 定义的分页信封（items、page、size、total）。 */
public record AiAgentPage(List<AiAgentDefinition> items, int page, int size, long total) {

    public AiAgentPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("page and size must be positive, total non-negative");
        }
    }
}
