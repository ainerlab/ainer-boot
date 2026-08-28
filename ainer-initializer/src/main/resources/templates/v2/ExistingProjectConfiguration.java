package {{package.name}}.initializer;

import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Initializer 增量切片装配。Mapper 接口自带 {@code @Mapper}，由宿主已有扫描配置或
 * MyBatis 自动配置登记，避免重复扫描；Authorization 模块若已由宿主装配，会自动组合
 * Workspace 自有策略贡献。
 */
@Configuration(proxyBeanMethods = false)
@Import(WorkspaceModuleConfiguration.class)
public class AinerInitializerWorkspaceConfiguration {
}
