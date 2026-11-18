package vn.tmc.edge.edgesync.infrastructure;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    static UUID generate(Instant now) {
        long millis = now.toEpochMilli();
        if (millis < 0 || millis > 0xFFFF_FFFF_FFFFL) {
            throw new IllegalArgumentException("Timestamp falls outside the UUIDv7 range");
        }
        // RFC 9562 section 5.7. The remaining 74 bits are random.
        long high = (millis << 16) | 0x7000L | RANDOM.nextInt(1 << 12);
        long low = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(high, low);
    }
}
