package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface NotificationRecordMapper {

    UUID insertReturningId(@Param("row") NotificationRecordRow row, @Param("now") Instant now);

    NotificationRecordRow selectById(@Param("id") UUID id);

    /**
     * PG 18 SKIP LOCKED claim: atomically selects pending records, locks them (SKIP LOCKED so
     * concurrent claimers don't block), and flips status to SENDING — all in one statement.
     */
    List<NotificationRecordRow> claimPending(@Param("batchSize") int batchSize, @Param("now") Instant now);

    void markSent(@Param("id") UUID id, @Param("sentAt") Instant sentAt, @Param("now") Instant now);

    void markFailed(@Param("id") UUID id, @Param("errorMessage") String errorMessage,
                    @Param("retryCount") int retryCount, @Param("maxRetries") int maxRetries,
                    @Param("nextRetryAt") Instant nextRetryAt, @Param("now") Instant now);

    List<NotificationRecordRow> selectPage(@Nullable @Param("status") String status,
            @Param("offset") long offset, @Param("limit") int limit);

    long countPage(@Nullable @Param("status") String status);
}
