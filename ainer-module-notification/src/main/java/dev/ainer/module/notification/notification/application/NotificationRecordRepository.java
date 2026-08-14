package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationRecord;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRecordRepository {

    UUID save(NotificationRecord record);

    Optional<NotificationRecord> findById(UUID id);

    List<NotificationRecord> claimPending(int batchSize);

    void markSent(UUID id, Instant sentAt);

    void markFailed(UUID id, String errorMessage, int retryCount, int maxRetries, Instant nextRetryAt);

    /** Page through delivery records, optionally filtered by status, newest first. */
    NotificationPageSlice<NotificationRecord> findPage(@Nullable String status, long offset, int size);
}
