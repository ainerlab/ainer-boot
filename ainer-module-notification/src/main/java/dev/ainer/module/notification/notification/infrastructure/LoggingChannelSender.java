package dev.ainer.module.notification.notification.infrastructure;

import dev.ainer.module.notification.notification.domain.ChannelSender;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认的 {@link ChannelSender}：把通知记录到日志而非真正发送。为每个渠道提供一个
 * 兜底 sender。启用对应开关后，WEBHOOK / EMAIL 分别由
 * {@link HttpWebhookChannelSender} / {@link SmtpMailChannelSender} 覆盖；SMS/Push
 * 仍由产品用真实适配器替换。
 *
 * <p>每个 Bean 按渠道命名（如 {@code smsSender}），产品可只覆盖单个渠道，
 * 其余渠道保留日志兜底。
 */
public sealed class LoggingChannelSender implements ChannelSender
        permits LoggingChannelSender.Sms, LoggingChannelSender.Email,
                LoggingChannelSender.Push, LoggingChannelSender.Webhook {

    private static final Logger log = LoggerFactory.getLogger(LoggingChannelSender.class);
    private final NotificationChannel channel;

    LoggingChannelSender(NotificationChannel channel) {
        this.channel = channel;
    }

    @Override
    public final NotificationChannel channel() {
        return channel;
    }

    @Override
    public void send(String recipient, String title, String body) {
        // 不记录 recipient/title/body 原文（可能含 PII/敏感内容），只记录渠道和脱敏哈希
        log.info("[NOTIFICATION:{}], recipientHash={}, titleLength={}, bodyLength={}",
                channel, Integer.toHexString(recipient.hashCode()), title.length(), body.length());
    }

    @Component("smsSender")
    @ConditionalOnMissingBean(name = "smsSender")
    public static final class Sms extends LoggingChannelSender {
        public Sms() { super(NotificationChannel.SMS); }
    }

    @Component("emailSender")
    @ConditionalOnMissingBean(name = "emailSender")
    @ConditionalOnProperty(prefix = "ainer.notification.email", name = "enabled", havingValue = "false", matchIfMissing = true)
    public static final class Email extends LoggingChannelSender {
        public Email() { super(NotificationChannel.EMAIL); }
    }

    @Component("pushSender")
    @ConditionalOnMissingBean(name = "pushSender")
    public static final class Push extends LoggingChannelSender {
        public Push() { super(NotificationChannel.PUSH); }
    }

    @Component("webhookSender")
    @ConditionalOnMissingBean(name = "webhookSender")
    @ConditionalOnProperty(prefix = "ainer.notification.webhook", name = "enabled", havingValue = "false", matchIfMissing = true)
    public static final class Webhook extends LoggingChannelSender {
        public Webhook() { super(NotificationChannel.WEBHOOK); }
    }
}
