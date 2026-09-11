package dev.ainer.cache.ratelimit;

import java.util.Objects;

/**
 * 固定窗口的公共算术与存储 key 布局（{@link RedisFixedWindowRateLimitPort} 与
 * {@link NodeLocalRateLimitPort} 共用，保证两个实现的 key 命名空间完全一致——运维排查时同一个
 * 业务 key 在两种后端下指向同一个逻辑键）。
 *
 * <p>窗口边界与 Unix epoch 对齐：{@code index = floorDiv(epochMillis, windowMillis)}，
 * 窗口区间是 {@code [index * windowMillis, (index + 1) * windowMillis)}。
 */
final class FixedWindow {

    private FixedWindow() {
    }

    /** 窗口序号（epoch 对齐，负数时间戳也能正确向下取整）。 */
    static long index(long epochMillis, long windowMillis) {
        return Math.floorDiv(epochMillis, windowMillis);
    }

    /** 窗口结束时刻（epoch 毫秒，等于下一窗口起点）。 */
    static long endMillis(long windowIndex, long windowMillis) {
        return Math.multiplyExact(windowIndex + 1, windowMillis);
    }

    /**
     * 存储 key：{@code <前缀><业务 key>:<窗口序号>}。
     *
     * <p>把窗口序号写进 key（而不是只依赖 TTL）是固定窗口最稳的实现方式：窗口推进后自然读到一个
     * 全新的计数键，不需要「检查旧值是否属于当前窗口再重置」的读改写竞态；旧键由 TTL 清理。
     */
    static String storageKey(String keyPrefix, String key, long windowIndex) {
        return keyPrefix + key + ":" + windowIndex;
    }

    /** 消耗 {@code permits} 后的剩余额度（下限 0）。 */
    static long remaining(long limit, long used) {
        return Math.max(limit - used, 0);
    }

    /** 参数校验（两个实现共用，错误信息一致）。 */
    static void requireValidArguments(String key, int permits, int limit, java.time.Duration window) {
        Objects.requireNonNull(window, "window");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("限流 key 不能为空");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits 必须 >= 1，当前为 " + permits);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须 >= 1，当前为 " + limit);
        }
        if (window.isZero() || window.isNegative() || window.toMillis() < 1) {
            throw new IllegalArgumentException("window 必须为正且不小于 1 毫秒，当前为 " + window);
        }
    }
}
