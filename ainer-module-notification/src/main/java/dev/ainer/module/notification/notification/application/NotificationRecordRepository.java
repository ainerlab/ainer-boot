package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRecordRepository {

    UUID save(NotificationRecord record);

    Optional<NotificationRecord> findById(UUID id);

    /**
     * Claim pending notifications for delivery using PG {@code FOR UPDATE SKIP LOCKED} — lock-free
     * multi-consumer queue without external MQ. Batch size limits memory and provides bounded
     * parallelism for {@code StructuredTaskScope}.
     *
     * @param batchSize max records to claim in one batch
     * @return claimed records (status flipped to SENDING atomically)
     */
    List<NotificationRecord> claimPending(int batchSize);

    /**
     * Mark a record as successfully sent.
     */
    void markSent(UUID id, Instant sentAt);

    /**
     * Mark a record as failed — either retryable (increment retry_count, schedule next_retry_at
     * with exponential backoff) or permanently FAILED if max_retries exhausted.
     */
    void markFailed(UUID id, String errorMessage, int retryCount, int maxRetries, Instant nextRetryAt);
}
