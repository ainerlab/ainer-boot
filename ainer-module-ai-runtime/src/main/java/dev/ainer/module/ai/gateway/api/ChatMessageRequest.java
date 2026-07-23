package dev.ainer.module.ai.gateway.api;

import dev.ainer.module.ai.gateway.domain.MessageRole;
import dev.ainer.module.ai.gateway.domain.ModelMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotNull MessageRole role,
        @NotBlank @Size(max = 100_000) String content) {

    ModelMessage toDomain() {
        return new ModelMessage(role, content);
    }
}
