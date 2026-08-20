package dev.ainer.module.config.config.application;

/**
 * 配置模块的 scope 常量（ADR-0040）。通过 {@code AuthenticatedPrincipal.hasScope(...)}
 * 命令式检查；运行时读取（getValue/getTyped/getSecret）是内部产品路径，
 * 保持不校验 scope。
 */
public final class ConfigAuthorities {

    /** 列出条目并读取变更历史。 */
    public static final String READ = "config.read";

    /** 设置普通值与 secret。 */
    public static final String MANAGE = "config.manage";

    private ConfigAuthorities() {
    }
}
