package dev.ainer.module.identity;

import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.module.identity.account.IdentityFeatureMarker;
import dev.ainer.module.identity.account.application.IdentityErrorCode;
import dev.ainer.module.identity.account.infrastructure.mybatis.IdentityMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = IdentityFeatureMarker.class)
@MapperScan(basePackageClasses = IdentityMapper.class)
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
}
