package dev.ainer.authorization.policy;

import java.util.UUID;

/**
 * Product-provided Agent definition status source (ADR-0043 A1). The authorization module never
 * depends on an AI runtime implementation; the default bean is fail-closed (UNKNOWN denies).
 */
public interface AgentDefinitionStatusResolver {

    enum AgentStatus {
        ACTIVE,
        RETIRED,
        UNKNOWN
    }

    /** Current status of the agent definition, evaluated at decision time. */
    AgentStatus agentStatus(UUID agentId);
}
