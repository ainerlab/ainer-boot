package dev.ainer.module.workspace;

import dev.ainer.core.error.ErrorCodeContributor;
import dev.ainer.module.workspace.workspace.WorkspaceFeatureMarker;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.infrastructure.mybatis.WorkspaceMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Workspace 模块的装配配置，由宿主应用通过 {@code @Import} 引入。
 *
 * <p>默认启用，可通过 {@code ainer.workspace.enabled=false} 关闭；负责扫描特性包、注册
 * MyBatis mapper、贡献模块错误码，并提供可覆盖的 UTC {@code Clock}。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ainer.workspace", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = WorkspaceFeatureMarker.class)
@MapperScan(basePackageClasses = WorkspaceMapper.class)
public class WorkspaceModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock workspaceClock() {
        return Clock.systemUTC();
    }

    @Bean
    ErrorCodeContributor workspaceErrorCodes() {
        return () -> List.of(WorkspaceErrorCode.values());
    }

}
