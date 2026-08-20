package dev.ainer.module.file.file.application;

/**
 * 文件模块的 scope 常量（ADR-0040）。通过 {@code AuthenticatedPrincipal.hasScope(...)}
 * 命令式检查；resource server 过滤器链只负责认证，scope 是模块自身职责。
 */
public final class FileAuthorities {

    /** 读取元数据并下载内容。 */
    public static final String READ = "file.read";

    /** 上传与删除。 */
    public static final String WRITE = "file.write";

    private FileAuthorities() {
    }
}
