package dev.ainer.module.ai.gateway.domain;

import dev.ainer.module.ai.AiRuntimeProperties;
import dev.ainer.module.ai.gateway.policy.CostCalculator;
import dev.ainer.module.ai.gateway.policy.PromptFingerprint;
import dev.ainer.module.ai.gateway.policy.SensitiveDataPolicy;
import dev.ainer.module.ai.gateway.policy.TenantRateLimiter;
import dev.ainer.module.ai.gateway.policy.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGatewayPolicyTest {

    private final List<ModelMessage> messages = List.of(
            new ModelMessage(MessageRole.SYSTEM, "Answer briefly"),
            new ModelMessage(MessageRole.USER, "Hello Ainer"));

    @Test
    void calculatesTokenCostWithoutFloatingPointLoss() {
        AiRuntimeProperties.Pricing pricing = new AiRuntimeProperties.Pricing(
                "USD", new BigDecimal("2.50"), new BigDecimal("10.00"));

        CostBreakdown cost = new CostCalculator(pricing).calculate(new TokenUsage(1_000, 2_000, false));

        assertThat(cost.amount()).isEqualByComparingTo("0.02250000");
        assertThat(cost.currency()).isEqualTo("USD");
    }

    @Test
    void estimatesUsageAndProducesStablePromptFingerprint() {
        TokenEstimator estimator = new TokenEstimator();
        PromptFingerprint fingerprint = new PromptFingerprint();

        assertThat(estimator.estimateInputTokens(messages)).isPositive();
        assertThat(fingerprint.digest(messages))
                .hasSize(64)
                .isEqualTo(fingerprint.digest(List.copyOf(messages)));
    }

    @Test
    void rejectsPrivateKeysAndProviderKeysBeforeNetworkAccess() {
        SensitiveDataPolicy policy = new SensitiveDataPolicy();

        assertThat(policy.containsDeniedData(List.of(
                new ModelMessage(MessageRole.USER, "-----BEGIN PRIVATE KEY----- secret")))).isTrue();
        assertThat(policy.containsDeniedData(List.of(
                new ModelMessage(MessageRole.USER, "sk-1234567890abcdefghijklmnop")))).isTrue();
        assertThat(policy.containsDeniedData(messages)).isFalse();
    }

    @Test
    void limitsRequestsPerTenantWithinTheMinuteWindow() {
        TenantRateLimiter limiter = new TenantRateLimiter(
                2, Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire("tenant-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a")).isFalse();
        assertThat(limiter.tryAcquire("tenant-b")).isTrue();
    }

    @Test
    void rejectsTokenUsageThatWouldOverflowThePublicTotal() {
        assertThatThrownBy(() -> new TokenUsage(Integer.MAX_VALUE, 1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void reportsNullAllowedModelAsConfigurationErrorInsteadOfBindingFailure() {
        AiRuntimeProperties.Provider provider = new AiRuntimeProperties.Provider(
                null, "https://provider.example", "test-secret", "test/model",
                Arrays.asList("test/model", null), null, null, false);
        AiRuntimeProperties properties = new AiRuntimeProperties(false, provider, null, null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-models");
    }
}
