package dev.ainer.module.ai.gateway.api;

import dev.ainer.module.ai.gateway.domain.CostBreakdown;

import java.math.BigDecimal;

public record CostResponse(BigDecimal amount, String currency) {

    static CostResponse from(CostBreakdown cost) {
        return new CostResponse(cost.amount(), cost.currency());
    }
}
