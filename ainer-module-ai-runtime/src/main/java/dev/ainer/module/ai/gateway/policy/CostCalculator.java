package dev.ainer.module.ai.gateway.policy;

import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.domain.CostBreakdown;
import dev.ainer.module.ai.gateway.domain.TokenUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 费用计算器：按配置的每百万 token 输入/输出单价与币种，把 TokenUsage 折算为
 * {@link CostBreakdown}（8 位小数、HALF_UP）。
 */
public final class CostCalculator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final BigDecimal inputPrice;
    private final BigDecimal outputPrice;
    private final String currency;

    public CostCalculator(AiRuntimeProperties.Pricing pricing) {
        this.inputPrice = pricing.getInputPerMillionTokens();
        this.outputPrice = pricing.getOutputPerMillionTokens();
        this.currency = pricing.getCurrency();
    }

    public CostBreakdown calculate(TokenUsage usage) {
        BigDecimal input = inputPrice.multiply(BigDecimal.valueOf(usage.inputTokens()));
        BigDecimal output = outputPrice.multiply(BigDecimal.valueOf(usage.outputTokens()));
        BigDecimal amount = input.add(output).divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        return new CostBreakdown(amount, currency);
    }
}
