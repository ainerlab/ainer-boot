package dev.ainer.authorizationserver.passkey;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ainer.security.authorization-server.passkey",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties({AinerPasskeyRecoveryProperties.class, AinerPasskeyEnrollmentProperties.class})
public class AinerPasskeyConfiguration {

    @Bean
    AinerPasskeySettings ainerPasskeySettings(
            AinerAuthorizationServerProperties properties) {
        return AinerPasskeySettings.from(properties);
    }

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcTemplate);
    }

    @Bean
    AinerJdbcPasskeyCredentialRepository userCredentialRepository(
            JdbcTemplate jdbcTemplate,
            PublicKeyCredentialUserEntityRepository userEntities,
            IdentityApplicationService identityService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            AinerPasskeyEnrollmentProperties enrollmentProperties) {
        return new AinerJdbcPasskeyCredentialRepository(
                jdbcTemplate,
                userEntities,
                identityService,
                transactionManager,
                clock,
                enrollmentProperties.isRequireInvite());
    }

    @Bean
    WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            AinerPasskeySettings settings,
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) {
        Webauthn4JRelyingPartyOperations operations =
                new Webauthn4JRelyingPartyOperations(
                        userEntities,
                        userCredentials,
                        PublicKeyCredentialRpEntity.builder()
                                .id(settings.rpId())
                                .name(settings.rpName())
                                .build(),
                        settings.allowedOrigins());
        operations.setCustomizeCreationOptions(options -> options
                .timeout(settings.ceremonyTimeout())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        operations.setCustomizeRequestOptions(options -> options
                .timeout(settings.ceremonyTimeout())
                .userVerification(UserVerificationRequirement.REQUIRED));
        return operations;
    }

    @Bean
    AinerPasskeyWebSecurity ainerPasskeyWebSecurity(
            AinerPasskeySettings settings,
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) {
        return new AinerPasskeyWebSecurity(settings, userEntities, userCredentials);
    }

    @Bean
    AinerPasskeyTenantSubjectGuard ainerPasskeyTenantSubjectGuard(JdbcTemplate jdbcTemplate) {
        return new AinerPasskeyTenantSubjectGuard(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.passkey.recovery",
            name = "self-service-enabled",
            havingValue = "true")
    AinerPasskeyRecoveryCodeService ainerPasskeyRecoveryCodeService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AinerJdbcPasskeyCredentialRepository credentialRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new AinerPasskeyRecoveryCodeService(
                jdbcTemplate, passwordEncoder, credentialRepository, transactionManager, clock);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.security.authorization-server.passkey.recovery",
            name = "enabled",
            havingValue = "true")
    AinerPasskeyAdminRecoveryService ainerPasskeyAdminRecoveryService(
            JdbcTemplate jdbcTemplate,
            AinerJdbcPasskeyCredentialRepository credentialRepository,
            AinerPasskeyTenantSubjectGuard tenantSubjectGuard,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new AinerPasskeyAdminRecoveryService(
                jdbcTemplate, credentialRepository, tenantSubjectGuard, transactionManager, clock);
    }

    @Bean
    AinerPasskeyEnrollmentGrantService ainerPasskeyEnrollmentGrantService(
            JdbcTemplate jdbcTemplate,
            AinerPasskeyTenantSubjectGuard tenantSubjectGuard,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        return new AinerPasskeyEnrollmentGrantService(
                jdbcTemplate, tenantSubjectGuard, transactionManager, clock);
    }
}
