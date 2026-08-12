package dev.ainer.module.notification.notification.domain;

/**
 * Lifecycle status of a notification record.
 *
 * <p>State machine: {@code PENDING → SENDING → SENT} or {@code SENDING → PENDING} (retry) or
 * {@code SENDING → FAILED} (max retries exhausted) or {@code PENDING → CANCELLED}.
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}
