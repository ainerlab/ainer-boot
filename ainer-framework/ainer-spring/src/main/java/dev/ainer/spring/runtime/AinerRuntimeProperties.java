package dev.ainer.spring.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ainer.runtime.*} 配置属性。mode 未配置时默认 {@link RuntimeMode#MONOLITH}。
 *
 * <p>运行模式只选择进程内或远程基础设施适配器，不改变部署拓扑、数据库归属或事务边界。
 */
@ConfigurationProperties("ainer.runtime")
public class AinerRuntimeProperties {

    private final RuntimeMode mode;

    public AinerRuntimeProperties(RuntimeMode mode) {
        this.mode = mode != null ? mode : RuntimeMode.MONOLITH;
    }

    public RuntimeMode getMode() {
        return mode;
    }
}
