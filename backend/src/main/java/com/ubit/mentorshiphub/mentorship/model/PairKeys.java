package com.ubit.mentorshiphub.mentorship.model;

import org.bson.types.ObjectId;

/**
 * Builds the canonical, order-independent identity of a user pair.
 *
 * <p>A mentorship relationship is the same relationship regardless of who
 * initiated it, so both a request {@code A -> B} and a request {@code B -> A}
 * must collapse to one key. The key is
 * {@code min(idA, idB) + "|" + max(idA, idB)} on the 24-character hex form, so
 * it is stable across restarts and safe to place a unique index on.
 *
 * <p>This is the single helper used everywhere a pair must be identified: the
 * request and mentorship documents both store the result, and the duplicate and
 * "already mentored" checks all go through here, so two code paths can never
 * disagree about what "the same pair" means.
 */
public final class PairKeys {

    private PairKeys() {
    }

    /**
     * The canonical key for the two users.
     *
     * @throws IllegalArgumentException if either id is null
     */
    public static String of(ObjectId first, ObjectId second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both user ids are required to form a pair key.");
        }
        String a = first.toHexString();
        String b = second.toHexString();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
