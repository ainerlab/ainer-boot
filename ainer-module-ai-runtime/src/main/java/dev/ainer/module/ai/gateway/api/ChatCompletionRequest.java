package dev.ainer.module.ai.gateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ChatCompletionRequest(
        @Size(max = 128) String model,
        @NotEmpty @Size(max = 100) List<@Valid ChatMessageRequest> messages,
        @Min(1) @Max(32_768) Integer maxOutputTokens,
        @DecimalMin("0") @DecimalMax("2") BigDecimal temperature) {

    int effectiveMaxOutputTokens() {
        return maxOutputTokens == null ? 1_024 : maxOutputTokens;
    }

    BigDecimal effectiveTemperature() {
        return temperature == null ? new BigDecimal("0.7") : temperature;
    }
}
