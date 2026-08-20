package dev.ainer.module.notification.notification.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code ainer_notification_record} 的 MyBatis mapper；SQL 位于 {@code mapper/notification/NotificationRecordMapper.xml}。
 * 队列领取与结果回写语句是投递引擎（SKIP LOCKED + 指数退避重试）的数据层基础。
 */
@Mapper
public interface NotificationRecordMapper {

    UUID insertReturningId(@Param("row") NotificationRecordRow row, @Param("now") Instant now);

    NotificationRecordRow selectById(@Param("id") UUID id);

    /**
     * PG 18 SKIP LOCKED 领取：原子地选出待处理记录、加锁（SKIP LOCKED 使并发领取者
     * 互不阻塞）并把状态翻转为 SENDING——全部在一条语句内完成。
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
