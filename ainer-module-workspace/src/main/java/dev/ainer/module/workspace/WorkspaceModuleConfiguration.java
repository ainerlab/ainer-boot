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
