package dev.ainer.module.notification.notification.domain;

/**
 * Notification delivery channel (ADR-0038). Designed for switch pattern matching dispatch:
 * {@code switch (channel) { case SMS sms -> ...; case EMAIL email -> ...; }}.
 */
public enum NotificationChannel {
    SMS,
    EMAIL,
    PUSH,
    WEBHOOK
}
