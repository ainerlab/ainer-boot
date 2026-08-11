package dev.ainer.authorization;

import dev.ainer.authorization.application.AuthorizationErrorCode;
import dev.ainer.authorization.catalog.PermissionContributor;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.core.error.ErrorCodeContributor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Module configuration for the authorization persistence slice (ADR-0030 S1). Assembled by the
 * host application via {@code @Import}. The feature is enabled by default and can be disabled
 * with {@code ainer.authorization.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.authorization", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = AuthorizationFeatureMarker.class)
@MapperScans(@MapperScan(basePackageClasses = AuthorizationFeatureMarker.class, annotationClass = Mapper.class))
public class AuthorizationModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock authorizationClock() {
        return Clock.systemUTC();
    }

    @Bean
    ErrorCodeContributor authorizationErrorCodes() {
        return () -> List.of(AuthorizationErrorCode.values());
    }

    /**
     * Build the in-memory {@link PermissionRegistry} from all registered {@link PermissionContributor}s.
     * The registry is the authority at decision time; the database catalog is only a management
     * projection. This bean is the S0 decision-engine entry point.
     */
    @Bean
    @ConditionalOnMissingBean
    PermissionRegistry permissionRegistry(List<PermissionContributor> contributors) {
        PermissionRegistry registry = new PermissionRegistry();
        for (PermissionContributor contributor : contributors) {
            registry.register(contributor);
        }
        return registry;
    }
}
