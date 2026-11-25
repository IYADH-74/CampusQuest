package com.campusquest.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 generator (RFC 9562): time-ordered for index locality, unguessable for enumeration resistance.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static UUID newId() {
        long timestamp = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;
        long randA = RANDOM.nextInt() & 0x0FFFL;
        long randB = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        long msb = (timestamp << 16) | 0x7000L | randA;
        long lsb = 0x8000_0000_0000_0000L | randB;
        return new UUID(msb, lsb);
    }
}
