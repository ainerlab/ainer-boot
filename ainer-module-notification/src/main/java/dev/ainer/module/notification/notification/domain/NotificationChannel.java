package dev.ainer.module.notification.notification.domain;

/**
 * 通知投递渠道（ADR-0038）。为 switch 模式匹配路由而设计：
 * {@code switch (channel) { case SMS sms -> ...; case EMAIL email -> ...; }}。
 */
public enum NotificationChannel {
    SMS,
    EMAIL,
    PUSH,
    WEBHOOK
}
