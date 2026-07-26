package dev.ainer.authorizationserver.config;

import dev.ainer.authorizationserver.passkey.AinerPasskeyWebSecurity;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.AinerSecurityScopes;
import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import dev.ainer.security.authorization.PrometheusEndpointRequestMatcher;
import dev.ainer.security.authorization.TenantlessServiceScopeAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ErrorAuthenticationFailureHandler;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class AinerAuthorizationServerWebSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            ObjectProvider<AinerPasskeyWebSecurity> passkeyProvider) throws Exception {
        AinerTokenIntrospectionAuthenticationProvider introspectionProvider =
                new AinerTokenIntrospectionAuthenticationProvider(
                        registeredClientRepository, authorizationService);
        AinerPasskeyWebSecurity passkey = passkeyProvider.getIfAvailable();
        http.oauth2AuthorizationServer(authorizationServer -> {
            http.securityMatcher(authorizationServer.getEndpointsMatcher());
            authorizationServer
                    .tokenIntrospectionEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
                        providers.clear();
                        providers.add(introspectionProvider);
                    }).errorResponseHandler((request, response, exception) -> {
                        if (exception instanceof OAuth2AuthenticationException oauthException
                                && OAuth2ErrorCodes.INVALID_CLIENT.equals(
                                        oauthException.getError().getErrorCode())) {
                            response.setStatus(401);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
                            response.setHeader(
                                    HttpHeaders.WWW_AUTHENTICATE,
                                    "Basic realm=\"oauth2/introspection\"");
                            response.getWriter().write("{\"error\":\"invalid_client\"}");
                            return;
                        }
                        new OAuth2ErrorAuthenticationFailureHandler()
                                .onAuthenticationFailure(request, response, exception);
                    }))
                    .oidc(Customizer.withDefaults());
        });
        http.authorizeHttpRequests(authorize -> {
                    if (passkey != null) {
                        authorize.requestMatchers("/oauth2/authorize")
                                .access(passkey.authorizationManager());
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(exceptions -> {
                    MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
                    html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
                    exceptions.defaultAuthenticationEntryPointFor(
                            new LoginUrlAuthenticationEntryPoint("/login"), html);
                });
        if (passkey != null) {
            http.webAuthn(passkey::configureProtocolChain);
        }
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain identityInternalApiSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        http.securityMatcher("/internal/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/internal/identity/"
                                        + "tenant-provisioning-notification-receipts")
                        .access(new TenantlessServiceScopeAuthorizationManager(
                                AinerSecurityScopes
                                        .IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_WRITE))
                        .requestMatchers("/internal/identity/directory/**")
                        .hasAnyAuthority(
                                "SCOPE_identity.directory.read",
                                "SCOPE_identity.directory.read.all")
                        .requestMatchers("/internal/identity/access-event-recovery/**")
                        .hasAnyAuthority(
                                "SCOPE_identity.access-events.replay.read",
                                "SCOPE_identity.access-events.replay.read.all",
                                "SCOPE_identity.access-events.replay.request",
                                "SCOPE_identity.access-events.replay.request.all",
                                "SCOPE_identity.access-events.replay.approve",
                                "SCOPE_identity.access-events.replay.approve.all")
                        .requestMatchers("/internal/oauth-service-clients/**")
                        .hasAuthority("SCOPE_" + AinerAuthorizationServerConfiguration
                                .CLIENT_CONTROL_MANAGE_SCOPE)
                        .requestMatchers("/internal/platform/identity/**")
                        .hasAnyAuthority(
                                "SCOPE_" + AinerSecurityScopes.PLATFORM_TENANTS_READ,
                                "SCOPE_" + AinerSecurityScopes.PLATFORM_TENANTS_WRITE,
                                "SCOPE_" + AinerSecurityScopes.PLATFORM_USERS_READ,
                                "SCOPE_" + AinerSecurityScopes.PLATFORM_USERS_WRITE)
                        .requestMatchers("/internal/passkey-recovery/**")
                        .hasAnyAuthority(
                                "SCOPE_passkey.recovery.request",
                                "SCOPE_passkey.recovery.request.all",
                                "SCOPE_passkey.recovery.approve",
                                "SCOPE_passkey.recovery.approve.all")
                        .requestMatchers("/internal/passkey-enrollment/**")
                        .hasAnyAuthority(
                                "SCOPE_passkey.enrollment.manage",
                                "SCOPE_passkey.enrollment.manage.all")
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain tenantProvisioningActivationSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        http.securityMatcher(
                        "/api/identity/tenant-activations/**",
                        "/api/me/tenant-provisioning-requests/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/identity/tenant-activations/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(
                                        request,
                                        response,
                                        StandardErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(
                                        request,
                                        response,
                                        StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(
                                        request,
                                        response,
                                        StandardErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain tenantMemberApiSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            OAuth2AuthorizationService authorizationService,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        http.securityMatcher(
                        "/api/me/access-token-revocations",
                        "/api/tenants/*/members",
                        "/api/tenants/*/members/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.FORBIDDEN)));
        http.addFilterAfter(
                new AinerAdminAccessTokenActiveFilter(
                        new DefaultBearerTokenResolver(),
                        authorizationService,
                        failureWriter),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(5)
    SecurityFilterChain authorizationServerMetricsSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            Environment environment,
            ObjectMapper objectMapper) throws Exception {
        AinerSecurityFailureWriter failureWriter = new AinerSecurityFailureWriter(objectMapper);
        http.securityMatcher(new PrometheusEndpointRequestMatcher(environment))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest()
                        .access(new TenantlessServiceScopeAuthorizationManager(
                                AinerSecurityScopes.PLATFORM_METRICS_READ)))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                failureWriter.write(request, response, StandardErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    @Order(6)
    SecurityFilterChain authorizationServerDefaultSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<AinerPasskeyWebSecurity> passkeyProvider,
            ObjectProvider<dev.ainer.authorizationserver.ratelimit.AinerLoginRateLimitFilter> rateLimitFilterProvider)
            throws Exception {
        AinerPasskeyWebSecurity passkey = passkeyProvider.getIfAvailable();
        http.authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (passkey != null) {
                        authorize.requestMatchers(
                                        "/webauthn/authenticate/options",
                                        "/login/webauthn")
                                .permitAll()
                                .requestMatchers(
                                        "/webauthn/register",
                                        "/webauthn/register/**")
                                .access(passkey.authorizationManager());
                    }
                    authorize.anyRequest().authenticated();
                });
        dev.ainer.authorizationserver.ratelimit.AinerLoginRateLimitFilter rateLimitFilter =
                rateLimitFilterProvider.getIfAvailable();
        if (rateLimitFilter != null) {
            http.addFilterAfter(
                    rateLimitFilter,
                    org.springframework.security.web.context.SecurityContextHolderFilter.class);
        }
        if (passkey != null) {
            http.formLogin(passkey::configureFormLogin);
            http.webAuthn(passkey::configureBrowserChain);
            // WebAuthn 协议 filter 在授权 filter 之前短路，凭证管理端点必须显式补一道条件 MFA 门禁。
            // 锚定 CsrfFilter 之后：CSRF 已校验、会话认证已恢复，且仍在 WebAuthn 协议 filter 之前。
            http.addFilterAfter(
                    passkey.credentialManagementGateFilter(),
                    org.springframework.security.web.csrf.CsrfFilter.class);
        } else {
            http.formLogin(Customizer.withDefaults());
        }
        return http.build();
    }
}
