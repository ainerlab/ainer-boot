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
 * 授权持久化切片的模块配置（ADR-0030 S1）。由宿主应用通过 {@code @Import} 装配。
 * 特性默认启用，可通过 {@code ainer.authorization.enabled=false} 关闭。
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
     * 从所有已注册的 {@link PermissionContributor} 构建内存态 {@link PermissionRegistry}。
     * 该注册表是决策时的权威；数据库目录只是管理投影。此 bean 是 S0 决策引擎入口。
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
     * 默认全拒绝的 {@link ScopePermissionCeiling}（ADR-0030 §3.3）。在产品模块注册显式
     * ceiling 之前，任何 OAuth scope 都不映射到任何权限。这强制保证"scope 绝不因名称
     * 相同而隐式变成权限"以及默认拒绝不变量。产品模块通过覆盖此 bean 声明真实的
     * scope→permission 上限。
     */
    @Bean
    @ConditionalOnMissingBean
    ScopePermissionCeiling denyAllScopePermissionCeiling() {
        return (scope, permission) -> false;
    }

    /**
     * 默认全拒绝的 {@link PublicAccessPolicy}（ADR-0030 §1、§5.2）。在产品模块注册显式
     * 策略之前不授予任何公开访问，这是匿名路径的默认拒绝。产品模块通过覆盖此 bean
     * 声明公开资源。
     */
    @Bean
    @ConditionalOnMissingBean
    PublicAccessPolicy denyAllPublicAccessPolicy() {
        return (permission, resource) -> Optional.empty();
    }

    /**
     * 默认全拒绝的 {@link DomainAuthorizationPolicy}（ADR-0030 §5.1）。{@code pathFor} 对
     * 每个权限都返回 null，因此在产品模块注册真实策略之前，决策引擎对所有已认证请求都
     * 发出 {@code UNKNOWN_POLICY} DENY。与全拒绝 ceiling、公开策略组合后，保证模块在
     * 没有产品配置被装配时端到端默认拒绝。产品模块通过覆盖此 bean 声明授权路径与
     * 关系/状态事实。
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
     * 装配 {@link AuthorizationService} 决策引擎（ADR-0030 §6）。注入注册表、ceiling、
     * 公开策略、领域策略、Binding 解析器与策略版本字符串。{@code @ConditionalOnMissingBean}
     * 允许产品模块在需要时替换整个引擎。
     *
     * @param policyVersion 用于审计/决策关联的稳定版本标签；默认为模块级常量，
     *                      可通过 {@code ainer.authorization.policy-version} 覆盖
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
     * Servlet 端点适配器，与应用层检查共用同一个决策服务。只有当宿主存在已验证主体
     * 解析器时才贡献此 bean；纯决策引擎消费者因此保持独立于 Spring Security 运行时装配。
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthenticatedPrincipalResolver.class)
    @ConditionalOnMissingBean
    AinerRequestAuthorizationManager ainerRequestAuthorizationManager(
            AuthorizationService authorizationService,
            AuthenticatedPrincipalResolver principalResolver,
            org.springframework.beans.factory.ObjectProvider<
                    dev.ainer.authorization.application.AuthorizationDecisionAuditService> decisionAudit,
            org.springframework.beans.factory.ObjectProvider<
                    dev.ainer.authorization.spring.AuthorizationTargetResolver> targetResolvers) {
        return new AinerRequestAuthorizationManager(
                authorizationService, principalResolver, decisionAudit, targetResolvers);
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
