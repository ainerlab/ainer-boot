package dev.ainer.module.ai.agent.api;

import dev.ainer.module.ai.agent.domain.AiAgentDefinition;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** {@link AiAgentDefinition} 的对外投影。 */
public record AgentResponse(
        UUID id,
        String code,
        String version,
        String status,
        String purpose,
        @Nullable String runtimeRef,
        @Nullable UUID workspaceId) {

    public static AgentResponse from(AiAgentDefinition agent) {
        return new AgentResponse(
                agent.id(),
                agent.code(),
                agent.version(),
                agent.status(),
                agent.purpose(),
                agent.runtimeRef(),
                agent.workspaceId());
    }
}
