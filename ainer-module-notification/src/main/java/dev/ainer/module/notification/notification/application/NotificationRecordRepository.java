package dev.ainer.module.notification.notification.application;

import dev.ainer.module.notification.notification.domain.NotificationRecord;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知投递记录的持久化端口（ADR-0040）。核心是 {@link #claimPending}：以 PG
 * {@code SKIP LOCKED} 语义领取一批可投递记录交给投递引擎发送，并在领取时写入租约；
 * 发送结果经 {@link #markSent}/{@link #markFailed} 回写，支撑重试与运维分页。
 *
 * <p>租约（lease）语义：领取者必须提供 {@code leaseOwner} 与 {@code leaseExpiresAt}。
 * 只要租约未过期，记录就不会被任何消费者再次领取——修复了 {@code SENDING} 无租约时
 * 「发送慢于轮询间隔 → 同一行被再次领取 → 重复投递」的缺陷；租约过期后允许重新领取，
 * 覆盖实例崩溃或发送线程卡死的自愈。写回时按 {@code (status='SENDING', leaseOwner)} 做
 * CAS：租约被他人接管后，旧领取者的迟到写回是空操作。投递语义为 at-least-once，
 * 发送方需保证幂等。
 */
public interface NotificationRecordRepository {

    UUID save(NotificationRecord record);

    Optional<NotificationRecord> findById(UUID id);

    /**
     * 领取至多 {@code batchSize} 条可投递记录，写入租约并翻转为 {@code SENDING}。
     *
     * @param leaseOwner     本次领取者标识（引擎实例 id），用于写回结果的 CAS
     * @param leaseExpiresAt 租约到期时间；到期前该记录不会被再次领取
     */
    List<NotificationRecord> claimPending(int batchSize, String leaseOwner, Instant leaseExpiresAt);

    /** 写回投递成功；要求记录仍处于 {@code SENDING} 且租约仍属于 {@code leaseOwner}。 */
    void markSent(UUID id, String leaseOwner, Instant sentAt);

    /** 写回投递失败并按 {@code retryCount}/{@code maxRetries} 决定回到 PENDING 还是终态 FAILED。 */
    void markFailed(UUID id, String leaseOwner, String errorMessage,
                    int retryCount, int maxRetries, Instant nextRetryAt);

    /** Page through delivery records, optionally filtered by status, newest first. */
    NotificationPageSlice<NotificationRecord> findPage(@Nullable String status, long offset, int size);
}
