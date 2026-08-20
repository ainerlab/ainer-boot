package dev.ainer.core.uuid;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 面向持久化身份的 RFC 9562 UUIDv7 生成器（ADR-0020、database-design-standard §14）。
 *
 * <p>用于在持久化路径中替代 {@code UUID.randomUUID()}（UUIDv4）。UUIDv7 按时间有序，
 * 可获得自然的索引局部性和可按时间排序的主键。数据库设计规范要求在 1.0 之前消除持久化
 * ID 对 {@code UUID.randomUUID()} 的使用。
 *
 * <p>本实现使用当前毫秒时间戳 + 12 位 version/variant + 74 位随机数。它不依赖数据库的
 * {@code DEFAULT uuidv7()}——而是在应用代码中生成 UUID，与既有的
 * {@code INSERT ... RETURNING id} 模式（由应用提供 ID）保持一致。
 */
public final class Uuidv7 {

    private Uuidv7() {
    }

    /**
     * 生成按时间有序的 UUIDv7。
     */
    public static UUID generate() {
        long timestampMs = System.currentTimeMillis();
        var random = ThreadLocalRandom.current();
        long msb = (timestampMs << 16) | (0x7L << 12) | random.nextInt(4096);
        long lsb = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
