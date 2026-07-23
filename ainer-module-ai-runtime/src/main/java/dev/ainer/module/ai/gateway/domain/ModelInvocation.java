package dev.ainer.module.ai.gateway.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ModelInvocation(
        UUID invocationId,
        String model,
        List<ModelMessage> messages,
        int maxOutputTokens,
        BigDecimal temperature) {

    public ModelInvocation {
        Objects.requireNonNull(invocationId, "invocationId");
        model = requireText(model, "model");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one model message is required");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        Objects.requireNonNull(temperature, "temperature");
        if (temperature.signum() < 0 || temperature.compareTo(new BigDecimal("2")) > 0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
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
