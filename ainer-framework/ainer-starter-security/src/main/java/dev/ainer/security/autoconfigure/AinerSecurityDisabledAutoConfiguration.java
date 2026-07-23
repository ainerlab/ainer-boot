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
 * Makes the starter's explicit opt-out deterministic instead of falling back to Boot's generated basic login.
 */
@AutoConfiguration(before = AinerResourceServerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "ainer.security.resource-server",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
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
