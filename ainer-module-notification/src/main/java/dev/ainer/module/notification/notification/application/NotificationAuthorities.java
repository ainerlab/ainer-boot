package dev.ainer.module.notification.notification.application;

/**
 * 通知模块的 scope 常量（ADR-0040）。
 */
public final class NotificationAuthorities {

    /** 分页查询模板与投递记录。 */
    public static final String READ = "notification.read";

    /** 创建/更新/禁用模板。 */
    public static final String MANAGE = "notification.manage";

    /** 提交通知意图以供投递。 */
    public static final String SUBMIT = "notification.submit";

    private NotificationAuthorities() {
    }
}
