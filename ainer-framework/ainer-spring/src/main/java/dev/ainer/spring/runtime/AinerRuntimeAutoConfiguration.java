package dev.ainer.spring.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Ainer 运行模式自动装配入口，启用 {@link AinerRuntimeProperties} 绑定。
 *
 * <p>仅负责注册 {@code ainer.runtime.*} 配置属性；具体 Bean 的按模式装配由
 * {@link ConditionalOnRuntimeMode} 在各模块的自动装配类中完成。
 */
@AutoConfiguration
@EnableConfigurationProperties(AinerRuntimeProperties.class)
public class AinerRuntimeAutoConfiguration {
}
