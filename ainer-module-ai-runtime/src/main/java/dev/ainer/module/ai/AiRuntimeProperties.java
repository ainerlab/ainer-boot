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

    private boolean enabled;
    private Provider provider = new Provider();
    private Limits limits = new Limits();
    private Pricing pricing = new Pricing();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public void setPricing(Pricing pricing) {
        this.pricing = pricing;
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

    public static class Provider {

        private String name = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private List<String> allowedModels = new ArrayList<>();
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private boolean allowInsecureHttp;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? null : name.trim();
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? null : baseUrl.trim();
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel == null ? null : defaultModel.trim();
        }

        public List<String> getAllowedModels() {
            return allowedModels;
        }

        public void setAllowedModels(List<String> allowedModels) {
            this.allowedModels = allowedModels == null
                    ? new ArrayList<>()
                    : allowedModels.stream()
                            .map(model -> model == null ? null : model.trim())
                            .toList();
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public void setAllowInsecureHttp(boolean allowInsecureHttp) {
            this.allowInsecureHttp = allowInsecureHttp;
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

    public static class Limits {

        private int requestsPerMinute = 60;
        private BigDecimal tenantDailyBudget = new BigDecimal("10.00");
        private int maxPromptCharacters = 100_000;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public BigDecimal getTenantDailyBudget() {
            return tenantDailyBudget;
        }

        public void setTenantDailyBudget(BigDecimal tenantDailyBudget) {
            this.tenantDailyBudget = tenantDailyBudget;
        }

        public int getMaxPromptCharacters() {
            return maxPromptCharacters;
        }

        public void setMaxPromptCharacters(int maxPromptCharacters) {
            this.maxPromptCharacters = maxPromptCharacters;
        }

        private void validate() {
            require(requestsPerMinute > 0 && requestsPerMinute <= 100_000,
                    "ainer.ai.limits.requests-per-minute is invalid");
            require(tenantDailyBudget != null && tenantDailyBudget.signum() > 0,
                    "ainer.ai.limits.tenant-daily-budget must be positive");
            require(maxPromptCharacters >= 1_000 && maxPromptCharacters <= 10_000_000,
                    "ainer.ai.limits.max-prompt-characters is invalid");
        }
    }

    public static class Pricing {

        private String currency = "USD";
        private BigDecimal inputPerMillionTokens = BigDecimal.ZERO;
        private BigDecimal outputPerMillionTokens = BigDecimal.ZERO;

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public BigDecimal getInputPerMillionTokens() {
            return inputPerMillionTokens;
        }

        public void setInputPerMillionTokens(BigDecimal inputPerMillionTokens) {
            this.inputPerMillionTokens = inputPerMillionTokens;
        }

        public BigDecimal getOutputPerMillionTokens() {
            return outputPerMillionTokens;
        }

        public void setOutputPerMillionTokens(BigDecimal outputPerMillionTokens) {
            this.outputPerMillionTokens = outputPerMillionTokens;
        }

        private void validate() {
            currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
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
