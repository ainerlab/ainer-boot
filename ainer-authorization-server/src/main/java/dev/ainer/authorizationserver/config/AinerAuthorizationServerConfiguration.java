package dev.ainer.authorizationserver.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
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

    private static final Logger log = LoggerFactory.getLogger(AinerAuthorizationServerConfiguration.class);

    public static final String CLIENT_INTROSPECTION_ALLOWED_SETTING = "ainer.introspection-allowed";
    public static final String TOKEN_PROFILE_SETTING = "ainer.token-profile";
    public static final String SEC_EPOCH_CLAIM = "sec_epoch";
    public static final String INTROSPECTION_CLIENT_SCOPE = "token.introspect";
    public static final String CLIENT_CONTROL_MANAGE_SCOPE = "oauth.clients.manage";
    public static final String BROWSER_CLIENT_CONTROL_MANAGE_SCOPE = "oauth.browser-clients.manage";
    /** 人员账号生命周期控制面（禁用/锁定/关闭/恢复、密码轮换、凭据撤销）的最小 scope。 */
    public static final String IDENTITY_ACCOUNT_CONTROL_MANAGE_SCOPE = "identity.accounts.manage";
    /** 服务主体生命周期控制面（禁用/恢复）的最小 scope，与人员账号 scope 分离。 */
    public static final String IDENTITY_SERVICE_PRINCIPAL_CONTROL_MANAGE_SCOPE =
            "identity.service-principals.manage";

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
    SigningKeyRing authorizationSigningKeyRing(
            AinerAuthorizationServerProperties properties,
            ResourceLoader resourceLoader) {
        SigningKeyRing ring = SigningKeyRing.load(
                properties.getSigningKey(),
                properties.getSigningKeyRing(),
                new PemRsaKeyLoader(resourceLoader));
        // 启动期打印一次即可判定「发布集 / 签发 key」是否符合预期——轮换 runbook 的可观测信号之一。
        log.info("Ainer authorization signing key ring: active={}, published={}",
                ring.activeKeyId(), ring.publishedKeyIds());
        if (ring.publishedKeyIds().size() > 1) {
            log.info("Ainer authorization signing key ring is in a rotation window: {} keys are published "
                            + "and tokens signed by any of them verify until the key stops being published",
                    ring.publishedKeyIds().size());
        }
        return ring;
    }

    @Bean
    @ConditionalOnMissingBean
    JWKSource<SecurityContext> authorizationJwkSource(SigningKeyRing signingKeyRing) {
        return signingKeyRing.jwkSource();
    }

    /**
     * 签发侧编码器：多把 RS256 key 同时发布时 {@code NimbusJwtEncoder} 默认直接抛
     * 「multiple keys」，因此必须显式给出选择策略——只选唯一带私钥材料的 key（当前激活 key）。
     * 不注册本 Bean 的话，SAS 会用同一 JWKSource 构造默认编码器，轮换过渡期一签发就失败。
     */
    @Bean
    @ConditionalOnMissingBean
    JwtEncoder authorizationJwtEncoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(SigningKeyRing::selectSigningKey);
        return encoder;
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
