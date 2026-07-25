package dev.ainer.authorizationserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityTokenStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AinerAuthorizationServerProperties.class)
public class AinerAuthorizationServerConfiguration {

    public static final String CLIENT_TENANT_SETTING = "ainer.tenant-id";
    public static final String CLIENT_INTROSPECTION_ALLOWED_SETTING = "ainer.introspection-allowed";
    public static final String INTROSPECTION_CLIENT_SCOPE = "token.introspect";
    public static final String CLIENT_CONTROL_MANAGE_SCOPE = "oauth.clients.manage";

    @Bean
    ManagedRegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new ManagedRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            ManagedRegisteredClientRepository registeredClientRepository,
            IdentityTokenStatusService identityTokenStatusService) {
        OAuth2AuthorizationService jdbc =
                jdbcAuthorizationService(jdbcTemplate, registeredClientRepository);
        return new RevocationAwareOAuth2AuthorizationService(
                jdbc,
                identityTokenStatusService,
                registeredClientRepository::isActiveByRegisteredClientId);
    }

    private JdbcOAuth2AuthorizationService jdbcAuthorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        JsonMapper jsonMapper = AinerOAuth2AuthorizationJsonMapperFactory.create();
        JdbcOAuth2AuthorizationService jdbc =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        jdbc.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(
                        registeredClientRepository,
                        jsonMapper));
        jdbc.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(
                        jsonMapper));
        return jdbc;
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AinerAuthorizationServerProperties properties) {
        String issuer = properties.getIssuer();
        if (issuer == null || issuer.isBlank() || !issuer.startsWith("https://")) {
            throw new IllegalStateException("Ainer authorization server issuer must be an explicit HTTPS URL");
        }
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    @ConditionalOnMissingBean
    JWKSource<SecurityContext> authorizationJwkSource(
            AinerAuthorizationServerProperties properties,
            ResourceLoader resourceLoader) {
        AinerAuthorizationServerProperties.SigningKey signingKey = properties.getSigningKey();
        if (signingKey.getKeyId() == null || signingKey.getKeyId().isBlank()) {
            throw new IllegalStateException("Ainer authorization signing key id is required");
        }
        PemRsaKeyLoader loader = new PemRsaKeyLoader(resourceLoader);
        RSAKey rsaKey = new RSAKey.Builder(loader.publicKey(signingKey.getPublicKeyLocation()))
                .privateKey(loader.privateKey(signingKey.getPrivateKeyLocation()))
                .keyID(signingKey.getKeyId())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder authorizationJwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            AinerAuthorizationServerProperties properties) {
        String issuer = properties.getIssuer();
        String audience = properties.getAudience();
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("Ainer authorization issuer and audience are required");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator, audienceValidator));
        return decoder;
    }

    @Bean
    AinerUserDetailsService ainerUserDetailsService(IdentityApplicationService identityService) {
        return new AinerUserDetailsService(identityService);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> ainerJwtTokenCustomizer(
            AinerAuthorizationServerProperties properties,
            AinerUserDetailsService userDetailsService) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                String audience = properties.getAudience();
                if (audience == null || audience.isBlank()) {
                    throw new IllegalStateException("Ainer authorization access-token audience is required");
                }
                context.getClaims().audience(new ArrayList<>(List.of(audience)));
            }
            Authentication authentication = context.getPrincipal();
            AinerUserDetails user = ainerUserDetails(authentication, userDetailsService);
            if (user != null) {
                context.getClaims()
                        .subject(user.subjectId().toString())
                        .claim("actor_type", "USER")
                        .claim("tenant_id", user.tenantId().toString())
                        .claim("roles", new ArrayList<>(roleNames(user)));
                List<FactorGrantedAuthority> factors = authentication.getAuthorities().stream()
                        .filter(FactorGrantedAuthority.class::isInstance)
                        .map(FactorGrantedAuthority.class::cast)
                        .toList();
                List<String> authenticationMethods = authenticationMethods(factors);
                if (!authenticationMethods.isEmpty()) {
                    context.getClaims().claim(
                            IdTokenClaimNames.AMR,
                            new ArrayList<>(authenticationMethods));
                    factors.stream()
                            .map(FactorGrantedAuthority::getIssuedAt)
                            .max(Instant::compareTo)
                            .ifPresent(authTime -> context.getClaims().claim(
                                    IdTokenClaimNames.AUTH_TIME,
                                    Date.from(authTime)));
                }
                return;
            }
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                RegisteredClient client = context.getRegisteredClient();
                context.getClaims()
                        .subject(client.getClientId())
                        .claim("actor_type", "SERVICE");
                String tenantId = client.getClientSettings().getSetting(CLIENT_TENANT_SETTING);
                if (tenantId != null && !tenantId.isBlank()) {
                    context.getClaims().claim("tenant_id", tenantId);
                }
            }
        };
    }

    private static AinerUserDetails ainerUserDetails(
            Authentication authentication,
            AinerUserDetailsService userDetailsService) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AinerUserDetails user) {
            return user;
        }
        // Passkey 用户经 WebAuthn 认证后，主体是协议 PublicKeyCredentialUserEntity（只含 username），
        // 需经 Identity 解析为 AinerUserDetails 才能投影稳定 sub/tenant_id/roles。
        if (principal instanceof PublicKeyCredentialUserEntity webAuthnUser) {
            UserDetails loaded = userDetailsService.loadUserByUsername(webAuthnUser.getName());
            if (loaded instanceof AinerUserDetails ainerUser) {
                return ainerUser;
            }
        }
        return null;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.machine-client-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerMachineClientBootstrapRunner ainerMachineClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        return new AinerMachineClientBootstrapRunner(properties, registeredClientRepository, passwordEncoder);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.introspection-client-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerIntrospectionClientBootstrapRunner ainerIntrospectionClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        return new AinerIntrospectionClientBootstrapRunner(
                properties, registeredClientRepository, passwordEncoder);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.metrics-client-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerMetricsClientBootstrapRunner ainerMetricsClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        return new AinerMetricsClientBootstrapRunner(
                properties, registeredClientRepository, passwordEncoder);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.client-control-operator-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerClientControlOperatorBootstrapRunner ainerClientControlOperatorBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        return new AinerClientControlOperatorBootstrapRunner(
                properties, registeredClientRepository, passwordEncoder);
    }

    private List<String> roleNames(AinerUserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
    }

    private List<String> authenticationMethods(
            List<FactorGrantedAuthority> factors) {
        Set<String> methods = new LinkedHashSet<>();
        if (hasFactor(factors, FactorGrantedAuthority.PASSWORD_AUTHORITY)) {
            methods.add("pwd");
        }
        if (hasFactor(factors, FactorGrantedAuthority.WEBAUTHN_AUTHORITY)) {
            methods.add("mfa");
            methods.add("pop");
        }
        return List.copyOf(methods);
    }

    private boolean hasFactor(
            List<FactorGrantedAuthority> factors,
            String authority) {
        return factors.stream()
                .anyMatch(factor -> authority.equals(factor.getAuthority()));
    }
}
