package dev.ainer.cache.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for distributed lock operations (ADR-0039). Products use this for idempotency control,
 * concurrent upload protection, and any cross-instance mutual exclusion.
 *
 * <p>Implementations:
 * <ul>
 *   <li>Redis: {@code SET key token NX EX ttl} + Lua-script release (token-checked);</li>
 *   <li>PostgreSQL: {@code pg_try_advisory_lock} (degraded fallback);</li>
 *   <li>In-memory: single-instance only, for development.</li>
 * </ul>
 */
public interface DistributedLockPort {

    /**
     * Attempt to acquire a lock with the given key and time-to-live.
     *
     * @param key lock identifier (namespaced, e.g. "upload:workspace-1:file-abc")
     * @param ttl lock auto-expiry (prevents deadlocks if the holder crashes)
     * @return the lock handle if acquired, or empty if already held by another caller
     */
    Optional<LockHandle> tryLock(String key, Duration ttl);

    /**
     * Release a previously acquired lock. The token must match — releasing a lock held by another
     * caller or an expired lock is a no-op.
     */
    void release(LockHandle handle);

    /** Opaque handle carrying the lock key and the unique token used for safe release. */
    record LockHandle(String key, String token) {}
}
