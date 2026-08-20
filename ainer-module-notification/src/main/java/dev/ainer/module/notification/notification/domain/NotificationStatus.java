package dev.ainer.module.notification.notification.domain;

/**
 * 通知记录的生命周期状态。
 *
 * <p>状态机：{@code PENDING → SENDING → SENT}，或 {@code SENDING → PENDING}（重试），
 * 或 {@code SENDING → FAILED}（重试次数耗尽），或 {@code PENDING → CANCELLED}。
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}
