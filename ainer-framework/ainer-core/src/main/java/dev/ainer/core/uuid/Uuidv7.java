package dev.ainer.core.uuid;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RFC 9562 UUIDv7 generator for persistent identity (ADR-0020, database-design-standard §14).
 *
 * <p>Replaces {@code UUID.randomUUID()} (UUIDv4) in persistence paths. UUIDv7 is time-ordered,
 * enabling natural index locality and chronologically sortable primary keys. The database-design
 * standard mandates elimination of {@code UUID.randomUUID()} for persistent IDs before 1.0.
 *
 * <p>This implementation uses the current millisecond timestamp + 12 bits of version/variant +
 * 74 bits of randomness. It does NOT rely on the database {@code DEFAULT uuidv7()} — it generates
 * the UUID in application code, consistent with the existing {@code INSERT ... RETURNING id}
 * pattern where the application supplies the ID.
 */
public final class Uuidv7 {

    private Uuidv7() {
    }

    /**
     * Generate a time-ordered UUIDv7.
     */
    public static UUID generate() {
        long timestampMs = System.currentTimeMillis();
        var random = ThreadLocalRandom.current();
        long msb = (timestampMs << 16) | (0x7L << 12) | random.nextInt(4096);
        long lsb = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
