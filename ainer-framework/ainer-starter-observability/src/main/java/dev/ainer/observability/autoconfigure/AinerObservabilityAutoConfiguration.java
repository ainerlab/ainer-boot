package dev.ainer.observability.autoconfigure;

import dev.ainer.observability.AinerObservabilityProperties;
import dev.ainer.observability.AinerOtlpExportMarker;
import dev.ainer.observability.RequestTraceCorrelationFilter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 最小观测装配：ObservationRegistry、requestId↔traceId 过滤器；OTLP 仅在
 * {@code ainer.observability.otlp.enabled=true} 时给出标记 bean。
 */
@AutoConfiguration
@EnableConfigurationProperties(AinerObservabilityProperties.class)
@ConditionalOnProperty(prefix = "ainer.observability", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public RequestTraceCorrelationFilter requestTraceCorrelationFilter(
            ObservationRegistry observationRegistry) {
        return new RequestTraceCorrelationFilter(observationRegistry);
    }

    @Bean
    @ConditionalOnBean(RequestTraceCorrelationFilter.class)
    public FilterRegistrationBean<RequestTraceCorrelationFilter> requestTraceCorrelationFilterRegistration(
            RequestTraceCorrelationFilter filter) {
        FilterRegistrationBean<RequestTraceCorrelationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("ainerRequestTraceCorrelationFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "ainer.observability.otlp", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public AinerOtlpExportMarker ainerOtlpExportMarker() {
        return new AinerOtlpExportMarker();
    }
}
