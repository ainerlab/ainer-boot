package dev.ainer.module.ai.agent.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Agent 注册请求。 */
public record RegisterAgentRequest(
        String code,
        String version,
        String purpose,
        @Nullable String runtimeRef,
        @Nullable UUID workspaceId) {
}
