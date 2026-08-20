package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationRecord;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知投递记录的持久化端口（ADR-0040）。核心是 {@link #claimPending}：以 PG
 * {@code SKIP LOCKED} 语义领取一批 PENDING 记录交给投递引擎发送；
 * 发送结果经 {@link #markSent}/{@link #markFailed} 回写，支撑重试与运维分页。
 */
public interface NotificationRecordRepository {

    UUID save(NotificationRecord record);

    Optional<NotificationRecord> findById(UUID id);

    List<NotificationRecord> claimPending(int batchSize);

    void markSent(UUID id, Instant sentAt);

    void markFailed(UUID id, String errorMessage, int retryCount, int maxRetries, Instant nextRetryAt);

    /** Page through delivery records, optionally filtered by status, newest first. */
    NotificationPageSlice<NotificationRecord> findPage(@Nullable String status, long offset, int size);
}
