package com.fooddelivery.notification.domain.util;

import java.security.SecureRandom;
import java.util.UUID;

public class UuidCreator {
    private static final SecureRandom random = new SecureRandom();

    public static UUID nextUuidV7() {
        long valueMs = System.currentTimeMillis();
        long mostSigBits = ((valueMs & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        long leastSigBits = 0x8000000000000000L | (random.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(mostSigBits, leastSigBits);
    }
}
