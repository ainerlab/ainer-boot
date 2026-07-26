package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.actor.AuthenticatedActorResolver;
import dev.ainer.security.authorization.PrometheusEndpointRequestMatcher;
import dev.ainer.security.authorization.TenantlessServiceScopeAuthorizationManager;
import dev.ainer.security.error.AinerSecurityErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.RestClientOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ainer.security.resource-server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AinerResourceServerProperties.class)
@EnableMethodSecurity
public class AinerResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthenticatedActorResolver authenticatedActorResolver(AinerResourceServerProperties properties) {
        return new SecurityContextAuthenticatedActorResolver(properties);
    }

    @Bean
    public ErrorCodeContributor ainerSecurityErrorCodes() {
        return () -> List.of(AinerSecurityErrorCode.values());
    }

    @Bean
    @ConditionalOnMissingBean(OpaqueTokenIntrospector.class)
    @ConditionalOnProperty(
            prefix = "ainer.security.resource-server.online-validation",
            name = "enabled",
            havingValue = "true")
    public OpaqueTokenIntrospector ainerOnlineTokenIntrospector(AinerResourceServerProperties properties) {
        AinerResourceServerProperties.OnlineValidation online = properties.getOnlineValidation();
        URI introspectionUri = online.validateAndGetIntrospectionUri();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(online.getConnectTimeout());
        requestFactory.setReadTimeout(online.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(online.getClientId(), online.getClientSecret()))
                .build();
        return new RestClientOpaqueTokenIntrospector(introspectionUri.toString(), restClient);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.resource-server.online-validation",
            name = "enabled",
            havingValue = "true")
    OnlineAccessTokenValidationFilter ainerOnlineAccessTokenValidationFilter(
            AinerResourceServerProperties properties,
            OpaqueTokenIntrospector introspector,
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectMapper objectMapper) {
        AinerResourceServerProperties.OnlineValidation online = properties.getOnlineValidation();
        online.validateAndGetIntrospectionUri();
        return new OnlineAccessTokenValidationFilter(
                online,
                new DefaultBearerTokenResolver(),
                introspector,
                new AinerSecurityFailureWriter(objectMapper),
                meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.resource-server.step-up",
            name = "enabled",
            havingValue = "true")
    RecentStrongAuthenticationFilter ainerRecentStrongAuthenticationFilter(
            AinerResourceServerProperties properties,
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<Clock> clock,
            ObjectMapper objectMapper) {
        AinerResourceServerProperties.StepUp stepUp = properties.getStepUp();
        stepUp.validate();
        return new RecentStrongAuthenticationFilter(
                stepUp,
                new AinerSecurityFailureWriter(objectMapper),
                meterRegistry.getIfAvailable(),
                clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain ainerResourceServerSecurityFilterChain(
            HttpSecurity http,
            AinerResourceServerProperties properties,
            ObjectProvider<OnlineAccessTokenValidationFilter> onlineValidationFilter,
            ObjectProvider<RecentStrongAuthenticationFilter> stepUpFilter,
            Environment environment,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(new PrometheusEndpointRequestMatcher(environment))
                        .access(new TenantlessServiceScopeAuthorizationManager(
                                AinerSecurityScopes.PLATFORM_METRICS_READ))
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.FORBIDDEN)));
        OnlineAccessTokenValidationFilter filter = onlineValidationFilter.getIfAvailable();
        if (filter != null) {
            http.addFilterAfter(filter, BearerTokenAuthenticationFilter.class);
        }
        RecentStrongAuthenticationFilter stepUp = stepUpFilter.getIfAvailable();
        if (stepUp != null) {
            http.addFilterAfter(stepUp, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
