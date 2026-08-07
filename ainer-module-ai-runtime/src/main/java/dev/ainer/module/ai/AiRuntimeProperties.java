package dev.ainer.module.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties("ainer.ai")
public class AiRuntimeProperties {

    private final boolean enabled;
    private final Provider provider;
    private final Limits limits;
    private final Pricing pricing;

    public AiRuntimeProperties(boolean enabled, Provider provider, Limits limits, Pricing pricing) {
        this.enabled = enabled;
        this.provider = provider != null ? provider
                : new Provider(null, null, null, null, null, null, null, false);
        this.limits = limits != null ? limits : new Limits(null, null, null);
        this.pricing = pricing != null ? pricing : new Pricing(null, null, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Provider getProvider() {
        return provider;
    }

    public Limits getLimits() {
        return limits;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public void validate() {
        require(provider != null, "ainer.ai.provider is required");
        require(limits != null, "ainer.ai.limits is required");
        require(pricing != null, "ainer.ai.pricing is required");
        provider.validate();
        limits.validate();
        pricing.validate();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static final class Provider {

        private final String name;
        private final String baseUrl;
        private final String apiKey;
        private final String defaultModel;
        private final List<String> allowedModels;
        private final Duration connectTimeout;
        private final Duration requestTimeout;
        private final boolean allowInsecureHttp;

        public Provider(
                String name,
                String baseUrl,
                String apiKey,
                String defaultModel,
                List<String> allowedModels,
                Duration connectTimeout,
                Duration requestTimeout,
                boolean allowInsecureHttp) {
            this.name = name != null && !name.isBlank() ? name.trim() : "openai-compatible";
            this.baseUrl = baseUrl != null ? baseUrl.trim() : null;
            this.apiKey = apiKey;
            this.defaultModel = defaultModel != null ? defaultModel.trim() : null;
            this.allowedModels = allowedModels != null
                    ? allowedModels.stream().map(model -> model == null ? null : model.trim()).toList()
                    : new ArrayList<>();
            this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
            this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(60);
            this.allowInsecureHttp = allowInsecureHttp;
        }

        public String getName() {
            return name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public List<String> getAllowedModels() {
            return allowedModels;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public List<String> effectiveAllowedModels() {
            return allowedModels == null || allowedModels.isEmpty()
                    ? List.of(defaultModel)
                    : allowedModels.stream().toList();
        }

        private void validate() {
            require(hasText(name) && name.matches("[a-z0-9][a-z0-9._-]{0,63}"),
                    "ainer.ai.provider.name is invalid");
            require(hasText(baseUrl), "ainer.ai.provider.base-url is required");
            URI uri;
            try {
                uri = URI.create(baseUrl);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("ainer.ai.provider.base-url is invalid", exception);
            }
            require(uri.getHost() != null, "ainer.ai.provider.base-url must include a host");
            require(uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null,
                    "ainer.ai.provider.base-url cannot contain user info, query or fragment");
            require("https".equalsIgnoreCase(uri.getScheme())
                            || (allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme())),
                    "ainer.ai.provider.base-url must use HTTPS");
            require(hasText(apiKey), "ainer.ai.provider.api-key is required");
            require(hasText(defaultModel), "ainer.ai.provider.default-model is required");
            require(defaultModel.length() <= 128, "ainer.ai.provider.default-model is too long");
            require(effectiveAllowedModels().stream().allMatch(model -> hasText(model) && model.length() <= 128),
                    "ainer.ai.provider.allowed-models contains an invalid model");
            require(effectiveAllowedModels().contains(defaultModel),
                    "ainer.ai.provider.allowed-models must contain the default model");
            require(connectTimeout != null && connectTimeout.isPositive(),
                    "ainer.ai.provider.connect-timeout must be positive");
            require(requestTimeout != null && requestTimeout.isPositive(),
                    "ainer.ai.provider.request-timeout must be positive");
        }
    }

    public static final class Limits {

        private final int requestsPerMinute;
        private final BigDecimal subjectDailyBudget;
        private final int maxPromptCharacters;

        public Limits(Integer requestsPerMinute, BigDecimal subjectDailyBudget, Integer maxPromptCharacters) {
            this.requestsPerMinute = requestsPerMinute != null ? requestsPerMinute : 60;
            this.subjectDailyBudget = subjectDailyBudget != null ? subjectDailyBudget : new BigDecimal("10.00");
            this.maxPromptCharacters = maxPromptCharacters != null ? maxPromptCharacters : 100_000;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public BigDecimal getSubjectDailyBudget() {
            return subjectDailyBudget;
        }

        public int getMaxPromptCharacters() {
            return maxPromptCharacters;
        }

        private void validate() {
            require(requestsPerMinute > 0 && requestsPerMinute <= 100_000,
                    "ainer.ai.limits.requests-per-minute is invalid");
            require(subjectDailyBudget != null && subjectDailyBudget.signum() > 0,
                    "ainer.ai.limits.subject-daily-budget must be positive");
            require(maxPromptCharacters >= 1_000 && maxPromptCharacters <= 10_000_000,
                    "ainer.ai.limits.max-prompt-characters is invalid");
        }
    }

    public static final class Pricing {

        private final String currency;
        private final BigDecimal inputPerMillionTokens;
        private final BigDecimal outputPerMillionTokens;

        public Pricing(String currency, BigDecimal inputPerMillionTokens, BigDecimal outputPerMillionTokens) {
            this.currency = currency != null && !currency.isBlank()
                    ? currency.trim().toUpperCase(Locale.ROOT)
                    : "USD";
            this.inputPerMillionTokens = inputPerMillionTokens != null
                    ? inputPerMillionTokens
                    : BigDecimal.ZERO;
            this.outputPerMillionTokens = outputPerMillionTokens != null
                    ? outputPerMillionTokens
                    : BigDecimal.ZERO;
        }

        public String getCurrency() {
            return currency;
        }

        public BigDecimal getInputPerMillionTokens() {
            return inputPerMillionTokens;
        }

        public BigDecimal getOutputPerMillionTokens() {
            return outputPerMillionTokens;
        }

        private void validate() {
            require(currency != null && currency.matches("[A-Z]{3}"),
                    "ainer.ai.pricing.currency must be an ISO-like three-letter code");
            require(inputPerMillionTokens != null && inputPerMillionTokens.signum() >= 0,
                    "ainer.ai.pricing.input-per-million-tokens cannot be negative");
            require(outputPerMillionTokens != null && outputPerMillionTokens.signum() >= 0,
                    "ainer.ai.pricing.output-per-million-tokens cannot be negative");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
