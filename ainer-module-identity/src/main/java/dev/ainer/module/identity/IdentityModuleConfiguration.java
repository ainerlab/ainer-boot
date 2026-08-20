package dev.ainer.module.identity;

import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.module.identity.foundation.CredentialRepository;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.HumanProfileRepository;
import dev.ainer.module.identity.foundation.IdentityFoundationMarker;
import dev.ainer.module.identity.foundation.IdentityErrorCode;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/**
 * Identity 模块装配入口：扫描 foundation 组件与 MyBatis Mapper，注册时钟、密码编码器、
 * 错误码贡献者与两个应用服务。可通过 {@code ainer.identity.enabled=false} 整体关闭。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.context.annotation.ComponentScan(basePackageClasses = IdentityFoundationMarker.class)
@MapperScans(@MapperScan(basePackageClasses = IdentityFoundationMarker.class, annotationClass = Mapper.class))
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
     * Greenfield foundation 应用核心（ADR-0033 Greenfield §3-§4）。ID 来源绑定到账号
     * 仓库的 {@code nextUuidV7()}，使持久化身份使用 PostgreSQL UUIDv7（ADR-0020）。
     */
    @Bean
    @ConditionalOnMissingBean
    IdentityFoundationService identityFoundationService(
            HumanAccountRepository accountRepository,
            LoginIdentityRepository loginIdentityRepository,
            CredentialRepository credentialRepository,
            HumanProfileRepository humanProfileRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        Supplier<java.util.UUID> idSource = accountRepository::nextUuidV7;
        return new IdentityFoundationService(
                accountRepository,
                loginIdentityRepository,
                credentialRepository,
                humanProfileRepository,
                passwordEncoder,
                clock,
                idSource);
    }

    /**
     * Greenfield ServicePrincipal 应用核心（ADR-0033 Greenfield §2.6）。ID 来源绑定到
     * principal 仓库的 {@code nextUuidV7()}。
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
