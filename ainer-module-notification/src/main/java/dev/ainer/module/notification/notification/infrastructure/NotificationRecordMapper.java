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
     * PG 18 SKIP LOCKED 领取：原子地选出可投递记录、加锁（SKIP LOCKED 使并发领取者
     * 互不阻塞）、写入租约并把状态翻转为 SENDING——全部在一条语句内完成。
     *
     * <p>可投递 = {@code PENDING} 且已到期，或 {@code SENDING} 且租约已过期；租约未过期的
     * {@code SENDING} 行不会被再次领取，避免慢发送被重复投递。
     */
    List<NotificationRecordRow> claimPending(@Param("batchSize") int batchSize,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("now") Instant now);

    /**
     * 写回投递成功。仅当记录仍是 {@code SENDING} 且租约仍属于本次领取者时生效，
     * 防止租约过期后旧领取者的迟到写回覆盖新领取者的状态。
     */
    void markSent(@Param("id") UUID id, @Param("leaseOwner") String leaseOwner,
                  @Param("sentAt") Instant sentAt, @Param("now") Instant now);

    /** 写回投递失败（按重试次数决定回到 PENDING 还是终态 FAILED）；CAS 条件同 {@link #markSent}。 */
    void markFailed(@Param("id") UUID id, @Param("leaseOwner") String leaseOwner,
                    @Param("errorMessage") String errorMessage,
                    @Param("retryCount") int retryCount, @Param("maxRetries") int maxRetries,
                    @Param("nextRetryAt") Instant nextRetryAt, @Param("now") Instant now);

    List<NotificationRecordRow> selectPage(@Nullable @Param("status") String status,
            @Param("offset") long offset, @Param("limit") int limit);

    long countPage(@Nullable @Param("status") String status);
}
