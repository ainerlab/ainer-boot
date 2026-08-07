package dev.ainer.authorizationserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AinerAuthorizationServerProperties.class)
public class AinerAuthorizationServerConfiguration {

    public static final String CLIENT_INTROSPECTION_ALLOWED_SETTING = "ainer.introspection-allowed";
    public static final String TOKEN_PROFILE_SETTING = "ainer.token-profile";
    public static final String SEC_EPOCH_CLAIM = "sec_epoch";
    public static final String INTROSPECTION_CLIENT_SCOPE = "token.introspect";
    public static final String CLIENT_CONTROL_MANAGE_SCOPE = "oauth.clients.manage";
    public static final String BROWSER_CLIENT_CONTROL_MANAGE_SCOPE = "oauth.browser-clients.manage";

    @Bean
    ManagedRegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new ManagedRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            ManagedRegisteredClientRepository registeredClientRepository,
            HumanAccountRepository humanAccountRepository,
            ServicePrincipalRepository servicePrincipalRepository) {
        OAuth2AuthorizationService jdbc =
                jdbcAuthorizationService(jdbcTemplate, registeredClientRepository);
        return new RevocationAwareOAuth2AuthorizationService(
                jdbc,
                humanAccountRepository,
                servicePrincipalRepository,
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
    AinerUserDetailsService ainerUserDetailsService(
            AinerAuthorizationServerProperties properties,
            IdentityFoundationService foundationService) {
        return new AinerUserDetailsService(foundationService, properties.getIssuer());
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> ainerJwtTokenCustomizer(
            AinerAuthorizationServerProperties properties,
            AinerUserDetailsService userDetailsService,
            ServicePrincipalFoundationService servicePrincipalFoundationService,
            HumanAccountRepository humanAccountRepository) {
        return new AinerJwtTokenCustomizer(
                properties,
                userDetailsService,
                servicePrincipalFoundationService,
                humanAccountRepository);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.machine-client-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerMachineClientBootstrapRunner ainerMachineClientBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder,
            ServicePrincipalFoundationService servicePrincipalFoundationService) {
        return new AinerMachineClientBootstrapRunner(
                properties, registeredClientRepository, passwordEncoder, servicePrincipalFoundationService);
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
            prefix = "ainer.security.authorization-server.browser-client-control-operator-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerBrowserClientOperatorBootstrapRunner ainerBrowserClientOperatorBootstrapRunner(
            AinerAuthorizationServerProperties properties,
            RegisteredClientRepository registeredClientRepository,
            PasswordEncoder passwordEncoder) {
        return new AinerBrowserClientOperatorBootstrapRunner(
                properties, registeredClientRepository, passwordEncoder);
    }

}
