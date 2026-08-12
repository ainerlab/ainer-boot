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
        log.info("[NOTIFICATION:{}], recipient={}, title={}, body={}", channel, recipient, title, body);
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
