package dev.ainer.module.ai.agent.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/** Agent 定义（ADR-0043）：授权参与者，不是认证凭据；modelId/runtime 不充当身份。 */
public record AiAgentDefinition(
        UUID id,
        String code,
        String version,
        String status,
        String purpose,
        @Nullable String runtimeRef,
        @Nullable UUID workspaceId,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant retiredAt) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
