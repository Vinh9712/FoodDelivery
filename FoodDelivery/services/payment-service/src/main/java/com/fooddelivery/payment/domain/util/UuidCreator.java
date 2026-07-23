package com.fooddelivery.payment.domain.util;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class UuidCreator {
    private static final SecureRandom random = new SecureRandom();
    private static final AtomicLong lastTimestampAndSequence = new AtomicLong();

    public static UUID nextUuidV7() {
        long candidate = System.currentTimeMillis() << 12;
        long timestampAndSequence = lastTimestampAndSequence.updateAndGet(
                previous -> Math.max(candidate, previous + 1));
        long valueMs = timestampAndSequence >>> 12;
        long sequence = timestampAndSequence & 0x0FFFL;
        long mostSigBits = ((valueMs & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | sequence;
        long leastSigBits = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(mostSigBits, leastSigBits);
    }
}
