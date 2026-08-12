package dev.ainer.module.notification.notification.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-or-update delivery record tracking the full lifecycle of a single notification send
 * (ADR-0038). Used by the SKIP LOCKED queue claimer and the retry scheduler.
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
