package dev.ainer.module.dictionary.dictionary.application;

/**
 * 字典模块的 scope 常量（ADR-0040）。由应用服务通过
 * {@code AuthenticatedPrincipal.hasScope(...)} 命令式检查。
 */
public final class DictionaryAuthorities {

    /** 读取类型/字典项并解析缓存投影。 */
    public static final String READ = "dictionary.read";

    /** 创建/更新/禁用类型与字典项。 */
    public static final String MANAGE = "dictionary.manage";

    private DictionaryAuthorities() {
    }
}
