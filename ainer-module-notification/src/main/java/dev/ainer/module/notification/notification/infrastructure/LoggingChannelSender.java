package dev.ainer.module.notification.notification.infrastructure;

import dev.ainer.module.notification.notification.domain.ChannelSender;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link ChannelSender} that logs notifications instead of sending them. Provides one
 * sender per channel as a fallback — products override with real adapters (SMS gateway, SMTP,
 * push service, webhook {@code RestClient}).
 *
 * <p>Each bean is named by channel (e.g. {@code smsSender}) so products can override individual
 * channels while keeping others as logging fallbacks.
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
    public static final class Webhook extends LoggingChannelSender {
        public Webhook() { super(NotificationChannel.WEBHOOK); }
    }
}
