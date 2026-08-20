package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.authorization.PrometheusEndpointRequestMatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * 显式退出的安全链：仅当宿主设置 {@code ainer.security.resource-server.enabled=false} 时装配。
 * 该属性没有默认值——未配置的应用回退到 Spring Boot 生成的默认链（所有请求都要求认证），
 * 绝不会回退到这条宽松链。若这里默认 fail-open，缺失一行配置就会把服务变成可匿名读取。
 */
@AutoConfiguration(before = AinerResourceServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "ainer.security.resource-server",
        name = "enabled",
        havingValue = "false")
public class AinerSecurityDisabledAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain ainerSecurityDisabledFilterChain(
            HttpSecurity http,
            Environment environment,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(new PrometheusEndpointRequestMatcher(environment)).denyAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.FORBIDDEN)));
        return http.build();
    }
}
