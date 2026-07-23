package dev.ainer.web.autoconfigure;

import dev.ainer.core.error.ErrorCodeRegistry;
import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.web.error.GlobalExceptionHandler;
import dev.ainer.web.request.RequestIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AinerWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrorCodeRegistry errorCodeRegistry(List<ErrorCodeContributor> contributors) {
        ErrorCodeRegistry registry = new ErrorCodeRegistry().register(List.of(StandardErrorCode.values()));
        contributors.forEach(contributor -> registry.register(contributor.errorCodes()));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter requestIdFilter) {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ainerRequestIdFilter");
        return registration;
    }
}
