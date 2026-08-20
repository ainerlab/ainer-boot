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

/**
 * Servlet web 应用的 Ainer web 自动装配。
 *
 * <p>注册错误码注册表（聚合 {@link ErrorCodeContributor}）、全局异常处理器与
 * {@code X-Request-Id} 请求关联过滤器；过滤器以最高优先级注册，保证所有响应携带
 * 请求追踪标识。全部 Bean 均可用同类型 Bean 覆盖。
 */
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
