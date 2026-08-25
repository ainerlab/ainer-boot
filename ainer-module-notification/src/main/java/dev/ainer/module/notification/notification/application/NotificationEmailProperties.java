package dev.ainer.module.notification.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EMAIL 渠道的可选 SMTP 投递（ADR-0040 SPI 可替换）。默认关闭，保留日志兜底。
 * 启用时必须配置 {@code from}，并装配 {@code JavaMailSender}（通常来自
 * {@code spring.mail.host}），否则启动失败关闭。
 */
@ConfigurationProperties("ainer.notification.email")
public record NotificationEmailProperties(boolean enabled, String from) {

    public NotificationEmailProperties {
        from = from == null ? "" : from.strip();
        if (enabled) {
            EmailAddressRules.validate(from);
        }
    }
}
