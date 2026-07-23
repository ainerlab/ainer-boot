package dev.ainer.module.ai.gateway.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record CostBreakdown(BigDecimal amount, String currency) {

    public CostBreakdown {
        amount = Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }
        currency = Objects.requireNonNull(currency, "currency").trim();
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must contain three uppercase letters");
        }
    }
}
