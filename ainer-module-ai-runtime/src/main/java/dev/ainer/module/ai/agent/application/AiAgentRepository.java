package dev.ainer.module.ai.agent.application;

import dev.ainer.module.ai.agent.domain.AiAgentDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Agent 定义持久化端口。 */
public interface AiAgentRepository {

    void insert(AiAgentDefinition agent);

    Optional<AiAgentDefinition> findById(UUID id);

    void retire(UUID id, Instant at);

    List<AiAgentDefinition> page(long offset, int limit);
}
