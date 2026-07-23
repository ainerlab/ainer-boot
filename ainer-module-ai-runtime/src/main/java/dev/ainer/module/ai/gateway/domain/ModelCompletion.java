package dev.ainer.module.ai.gateway.domain;

import java.util.Objects;

public record ModelCompletion(
        String providerRequestId,
        String model,
        String content,
        String finishReason,
        TokenUsage usage) {

    public ModelCompletion {
        providerRequestId = requireText(providerRequestId, "providerRequestId");
        model = requireText(model, "model");
        content = Objects.requireNonNull(content, "content");
        finishReason = finishReason == null || finishReason.isBlank() ? "unknown" : finishReason;
        Objects.requireNonNull(usage, "usage");
        if (providerRequestId.length() > 160) {
            throw new IllegalArgumentException("providerRequestId is too long");
        }
        if (model.length() > 128) {
            throw new IllegalArgumentException("model is too long");
        }
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
