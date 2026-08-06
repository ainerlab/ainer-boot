package dev.ainer.module.identity;

import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.module.identity.account.IdentityFeatureMarker;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.module.identity.account.infrastructure.mybatis.IdentityMapper;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationMarker;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityRepository;
import dev.ainer.module.identity.foundation.OAuthClientBindingRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = {IdentityFeatureMarker.class, IdentityFoundationMarker.class})
@MapperScans({
        @MapperScan(basePackageClasses = IdentityMapper.class),
        @MapperScan(basePackageClasses = IdentityFoundationMarker.class, annotationClass = Mapper.class)
})
public class IdentityModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder identityPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    ErrorCodeContributor identityErrorCodes() {
        return () -> List.of(IdentityErrorCode.values());
    }

    /**
     * Greenfield foundation application core (ADR-0033 Greenfield §3-§4). The id source binds to the account
     * repository's {@code nextUuidV7()} so persisted identities use PostgreSQL UUIDv7 (ADR-0020).
     */
    @Bean
    @ConditionalOnMissingBean
    IdentityFoundationService identityFoundationService(
            HumanAccountRepository accountRepository,
            LoginIdentityRepository loginIdentityRepository,
            Clock clock) {
        Supplier<java.util.UUID> idSource = accountRepository::nextUuidV7;
        return new IdentityFoundationService(accountRepository, loginIdentityRepository, clock, idSource);
    }

    /**
     * Greenfield ServicePrincipal application core (ADR-0033 Greenfield §2.6). The id source binds to the
     * principal repository's {@code nextUuidV7()}.
     */
    @Bean
    @ConditionalOnMissingBean
    ServicePrincipalFoundationService servicePrincipalFoundationService(
            ServicePrincipalRepository principalRepository,
            OAuthClientBindingRepository bindingRepository,
            Clock clock) {
        Supplier<java.util.UUID> idSource = principalRepository::nextUuidV7;
        return new ServicePrincipalFoundationService(principalRepository, bindingRepository, clock, idSource);
    }
}
