package dev.ainer.cache.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式锁操作端口（ADR-0039）。产品可用于幂等控制、并发上传保护以及任何跨实例互斥。
 *
 * <p>实现：
 * <ul>
 *   <li>Redis：{@code SET key token NX EX ttl} + Lua 脚本校验 token 后释放；</li>
 *   <li>PostgreSQL：{@code pg_try_advisory_lock}（降级备选）；</li>
 *   <li>进程内：仅限单实例，供开发使用。</li>
 * </ul>
 */
public interface DistributedLockPort {

    /**
     * 尝试以指定 key 与存活时间获取锁。
     *
     * @param key 锁标识（带命名空间，例如 "upload:workspace-1:file-abc"）
     * @param ttl 锁自动过期时间（防止持有者崩溃后死锁）
     * @return 获取成功返回锁句柄；已被其他调用方持有时返回 empty
     */
    Optional<LockHandle> tryLock(String key, Duration ttl);

    /**
     * 释放此前获取的锁。token 必须匹配——释放他人持有的锁或已过期的锁是 no-op。
     */
    void release(LockHandle handle);

    /** 携带锁 key 与唯一 token 的不透明句柄，用于安全释放。 */
    record LockHandle(String key, String token) {}
}
