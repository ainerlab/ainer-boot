package dev.ainer.module.ai.gateway.domain;

import java.util.Objects;

public record ModelMessage(MessageRole role, String content) {

    public ModelMessage {
        Objects.requireNonNull(role, "role");
        content = Objects.requireNonNull(content, "content");
        if (content.isBlank()) {
            throw new IllegalArgumentException("Model message content cannot be blank");
        }
    }
}
