package dev.ainer.authorization;

import dev.ainer.authorization.application.AuthorizationErrorCode;
import dev.ainer.authorization.catalog.PermissionContributor;
import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;
import dev.ainer.authorization.spring.AinerAuthorizeInterceptor;
import dev.ainer.authorization.spring.AinerRequestAuthorizationManager;
import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import org.apache.ibatis.annotations.Mapper;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
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

    /** 默认 fail-closed：无产品 Agent 注册表时，一切委托检查点对 Agent 状态返回 UNKNOWN。 */
    @Bean
    @ConditionalOnMissingBean
    dev.ainer.authorization.policy.AgentDefinitionStatusResolver denyAllAgentStatusResolver() {
        return agentId -> dev.ainer.authorization.policy.AgentDefinitionStatusResolver.AgentStatus.UNKNOWN;
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

    /**
     * Default deny-all {@link ScopePermissionCeiling} (ADR-0030 §3.3). No OAuth scope maps to any
     * permission until a product module registers an explicit ceiling. This enforces "a scope never
     * becomes a permission implicitly by name equality" and the default-deny invariant.
     * Product modules override this bean to declare real scope→permission ceilings.
     */
    @Bean
    @ConditionalOnMissingBean
    ScopePermissionCeiling denyAllScopePermissionCeiling() {
        return (scope, permission) -> false;
    }

    /**
     * Default deny-all {@link PublicAccessPolicy} (ADR-0030 §1, §5.2). No public access is granted
     * until a product module registers an explicit policy. This is the anonymous-path default-deny.
     * Product modules override this bean to declare public resources.
     */
    @Bean
    @ConditionalOnMissingBean
    PublicAccessPolicy denyAllPublicAccessPolicy() {
        return (permission, resource) -> Optional.empty();
    }

    /**
     * Default deny-all {@link DomainAuthorizationPolicy} (ADR-0030 §5.1). {@code pathFor} returns null
     * for every permission, so the decision engine emits {@code UNKNOWN_POLICY} DENY for all
     * authenticated requests until a product module registers a real policy. Combined with the
     * deny-all ceiling and public policy, this guarantees end-to-end default-deny when the module is
     * assembled without product configuration.
     * Product modules override this bean to declare grant paths and relation/state facts.
     */
    @Bean
    @ConditionalOnMissingBean
    DomainAuthorizationPolicy denyAllDomainAuthorizationPolicy() {
        return new DomainAuthorizationPolicy() {
            @Override
            public @Nullable GrantPath pathFor(PermissionCode permission) {
                return null;
            }

            @Override
            public boolean relationGrants(
                    Requester.Authenticated subject, PermissionCode permission,
                    ResourceRef resource, AuthorizationContext context) {
                return false;
            }

            @Override
            public boolean resourceStateSatisfies(
                    Requester.Authenticated subject, PermissionCode permission,
                    ResourceRef resource, AuthorizationContext context) {
                return false;
            }
        };
    }

    /**
     * Assemble the {@link AuthorizationService} decision engine (ADR-0030 §6). Injects the registry,
     * ceiling, public policy, domain policy, binding resolver and a policy version string.
     * {@code @ConditionalOnMissingBean} allows product modules to replace the entire engine if needed.
     *
     * @param policyVersion stable version label for audit/decision correlation; defaults to a
     *                      module-level constant, overridable via {@code ainer.authorization.policy-version}
     */
    @Bean
    @ConditionalOnMissingBean
    AuthorizationService authorizationService(
            PermissionRegistry permissionRegistry,
            ScopePermissionCeiling scopeCeiling,
            PublicAccessPolicy publicAccessPolicy,
            DomainAuthorizationPolicy domainPolicy,
            BindingResolver bindingResolver,
            dev.ainer.authorization.policy.SubjectSetMembershipRegistry setMembershipRegistry,
            @Value("${ainer.authorization.policy-version:ainer-authorization-default}") String policyVersion) {
        return new AuthorizationService(
                permissionRegistry, scopeCeiling, publicAccessPolicy,
                domainPolicy, bindingResolver, setMembershipRegistry, policyVersion);
    }

    /**
     * Servlet endpoint adapter backed by the same decision service as application-level checks.
     * It is only contributed when the host has a verified-principal resolver; pure decision-engine
     * consumers remain independent of Spring Security runtime assembly.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthenticatedPrincipalResolver.class)
    @ConditionalOnMissingBean
    AinerRequestAuthorizationManager ainerRequestAuthorizationManager(
            AuthorizationService authorizationService,
            AuthenticatedPrincipalResolver principalResolver,
            org.springframework.beans.factory.ObjectProvider<
                    dev.ainer.authorization.application.AuthorizationDecisionAuditService> decisionAudit) {
        return new AinerRequestAuthorizationManager(authorizationService, principalResolver, decisionAudit);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AinerRequestAuthorizationManager.class)
    @ConditionalOnMissingBean
    AinerAuthorizeInterceptor ainerAuthorizeInterceptor(
            AinerRequestAuthorizationManager authorizationManager) {
        return new AinerAuthorizeInterceptor(authorizationManager);
    }

    @Bean("ainerAuthorizationWebMvcConfigurer")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AinerAuthorizeInterceptor.class)
    WebMvcConfigurer ainerAuthorizationWebMvcConfigurer(AinerAuthorizeInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
