package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 追加或更新的投递记录，跟踪单条通知发送的完整生命周期（ADR-0038）。
 * 供 SKIP LOCKED 队列领取器与重试调度器使用。
 */
public record NotificationRecord(
        UUID id,
        @Nullable String templateCode,
        NotificationChannel channel,
        String recipient,
        @Nullable String title,
        @Nullable String body,
        @Nullable Map<String, Object> payload,
        NotificationStatus status,
        int retryCount,
        int maxRetries,
        @Nullable Instant nextRetryAt,
        @Nullable String errorMessage,
        @Nullable Instant sentAt,
        Instant createdAt,
        Instant updatedAt) {

    public NotificationRecord {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        payload = payload == null ? null : Map.copyOf(payload);
    }
}
