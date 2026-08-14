package dev.ainer.module.organization.orgdir.application;

/**
 * 组织目录 scope（ADR-0042 §5.2）：资源服务器安全链只做认证，scope 在应用服务内对已验证
 * principal 强制（与 P3 模块惯例一致）。
 */
public final class OrganizationAuthorities {

    /** 读取目录、单元、任职与成员投影。 */
    public static final String READ = "organization.read";

    /** 全部管理命令（创建/调岗/暂停/终止等）。 */
    public static final String MANAGE = "organization.manage";

    private OrganizationAuthorities() {
    }
}
