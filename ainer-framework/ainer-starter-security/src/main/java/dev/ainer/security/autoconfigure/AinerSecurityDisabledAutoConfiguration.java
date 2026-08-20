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
 * Explicit opt-out chain: assembled only when the host sets
 * {@code ainer.security.resource-server.enabled=false}. The property has no default — an
 * unconfigured application falls back to Spring Boot's generated default chain (everything
 * requires authentication), never to this permissive one. Leaving this default fail-open would
 * turn a missing property line into an anonymously readable service.
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
