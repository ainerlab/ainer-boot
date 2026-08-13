package dev.ainer.module.ai;

import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.module.ai.gateway.AiGatewayFeatureMarker;
import dev.ainer.module.ai.gateway.application.AiGatewayErrorCode;
import dev.ainer.module.ai.gateway.application.ModelProvider;
import dev.ainer.module.ai.gateway.infrastructure.mybatis.AiInvocationMapper;
import dev.ainer.module.ai.gateway.infrastructure.openai.OpenAiCompatibleModelProvider;
import dev.ainer.module.ai.gateway.policy.CostCalculator;
import dev.ainer.module.ai.gateway.policy.PromptFingerprint;
import dev.ainer.module.ai.gateway.policy.SensitiveDataPolicy;
import dev.ainer.module.ai.gateway.policy.SubjectRateLimiter;
import dev.ainer.module.ai.gateway.policy.TokenEstimator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.ai", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiRuntimeProperties.class)
@ComponentScan(basePackageClasses = AiGatewayFeatureMarker.class)
@MapperScan(basePackageClasses = AiInvocationMapper.class)
public class AiRuntimeModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock aiRuntimeClock() {
        return Clock.systemUTC();
    }

    @Bean
    ErrorCodeContributor aiRuntimeErrorCodes() {
        return () -> List.of(AiGatewayErrorCode.values());
    }

    // P0-2 出站 HTTP 例外（ADR-0029 第 2 项）：AI provider 使用 JDK HttpClient 而非 Boot 管理的
    // RestClient.Builder，因为 SSE 流式响应需要逐帧解析与可中断的流控制，RestClient 的缓冲式请求
    // 模型无法满足。此例外为刻意设计，不得为“统一出站 HTTP”将其改回 RestClient。
    @Bean
    HttpClient aiProviderHttpClient(AiRuntimeProperties properties) {
        properties.validate();
        return HttpClient.newBuilder()
                .connectTimeout(properties.getProvider().getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    TokenEstimator aiTokenEstimator() {
        return new TokenEstimator();
    }

    @Bean
    CostCalculator aiCostCalculator(AiRuntimeProperties properties) {
        properties.validate();
        return new CostCalculator(properties.getPricing());
    }

    @Bean
    PromptFingerprint aiPromptFingerprint() {
        return new PromptFingerprint();
    }

    @Bean
    SensitiveDataPolicy aiSensitiveDataPolicy() {
        return new SensitiveDataPolicy();
    }

    @Bean
    SubjectRateLimiter aiSubjectRateLimiter(AiRuntimeProperties properties, Clock clock) {
        properties.validate();
        return new SubjectRateLimiter(properties.getLimits().getRequestsPerMinute(), clock);
    }

    // 仅用于 AI SSE 流式任务，按名显式注入；标记 defaultCandidate=false 避免被当作 Boot 通用
    // TaskExecutor/ExecutorService 默认候选，从而不影响 MVC 异步、@Async 与虚拟线程自动配置（ADR-0029 第 5 项）。
    @Bean(defaultCandidate = false, destroyMethod = "close")
    ExecutorService aiStreamExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("ainer-ai-stream-", 0).factory());
    }

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    ModelProvider openAiCompatibleModelProvider(
            AiRuntimeProperties properties,
            HttpClient aiProviderHttpClient,
            ObjectMapper objectMapper,
            TokenEstimator tokenEstimator) {
        return new OpenAiCompatibleModelProvider(
                properties.getProvider(), aiProviderHttpClient, objectMapper, tokenEstimator);
    }
}
