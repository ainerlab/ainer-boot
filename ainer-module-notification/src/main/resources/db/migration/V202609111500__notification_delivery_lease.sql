-- 通知投递租约（ADR-0038 投递引擎加固，2026-09-11）。
--
-- 背景：claimPending 原先把 status IN ('PENDING','SENDING') 一起领取，而 SENDING 没有租约，
-- 发送慢于 poll-interval-ms 时同一行会被下一轮（或其他实例）再次领取并重复发送。
--
-- 方案：领取时写入 lease_owner + lease_expires_at；只有 PENDING 到期记录，或租约已过期的
-- SENDING 记录，才允许被领取。发送完成/失败回写时按 (status='SENDING', lease_owner) CAS
-- 并清空租约，过期租约的旧领取者无法覆盖新领取者的状态。
--
-- 兼容性：本 migration 只新增列/约束/索引，不修改任何已发布 migration。迁移前遗留的
-- SENDING 行没有租约，按新语义将永远无法再领取（永久卡在中间态）；它们在旧代码下本来
-- 就是「可被立即重新领取」的，因此统一复位为 PENDING 交回正常重试路径。投递是
-- at-least-once，发送方需保证幂等。

ALTER TABLE ainer_notification_record
    ADD COLUMN lease_owner VARCHAR(96),
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

-- 复位遗留 SENDING（无租约）行，避免其在新语义下永久卡住。
UPDATE ainer_notification_record
SET status = 'PENDING',
    next_retry_at = now(),
    updated_at = now()
WHERE status = 'SENDING';

-- 不变量：SENDING 必须有租约到期时间。复位语句保证存量行满足该约束。
ALTER TABLE ainer_notification_record
    ADD CONSTRAINT ck_ainer_notification_record_sending_lease
        CHECK (status <> 'SENDING' OR lease_expires_at IS NOT NULL);

-- 支持「租约过期的 SENDING 可重新领取」这一分支；PENDING 分支继续使用
-- idx_ainer_notification_record_status_retry。
CREATE INDEX idx_ainer_notification_record_sending_lease
    ON ainer_notification_record (lease_expires_at)
    WHERE status = 'SENDING';
