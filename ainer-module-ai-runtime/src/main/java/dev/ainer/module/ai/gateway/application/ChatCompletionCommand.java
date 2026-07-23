package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.ModelMessage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ChatCompletionCommand(
        InvocationContext context,
        String requestedModel,
        List<ModelMessage> messages,
        int maxOutputTokens,
        BigDecimal temperature) {

    public ChatCompletionCommand {
        Objects.requireNonNull(context, "context");
        requestedModel = requestedModel == null ? "" : requestedModel.trim();
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        Objects.requireNonNull(temperature, "temperature");
    }
}
