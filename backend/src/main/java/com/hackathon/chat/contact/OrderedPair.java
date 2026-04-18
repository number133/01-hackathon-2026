package com.hackathon.chat.contact;

import java.util.UUID;

/**
 * UUIDs are compared UNSIGNED to match Postgres's native uuid ordering.
 * Java's {@link UUID#compareTo} uses signed-long semantics on the
 * most/least significant bits, which disagrees with Postgres for any
 * UUID whose high bit is set — those would end up as "low" in Java but
 * "high" in the database, tripping our ordered-pair check constraints.
 */
public record OrderedPair(UUID low, UUID high) {

    public static OrderedPair of(UUID a, UUID b) {
        int cmp = unsignedCompare(a, b);
        if (cmp == 0) {
            throw new IllegalArgumentException("Both ids are equal");
        }
        return cmp < 0 ? new OrderedPair(a, b) : new OrderedPair(b, a);
    }

    public static int unsignedCompare(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (msb != 0) return msb;
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
