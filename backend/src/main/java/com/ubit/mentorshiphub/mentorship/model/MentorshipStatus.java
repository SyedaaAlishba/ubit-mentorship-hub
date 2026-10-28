package com.ubit.mentorshiphub.mentorship.model;

/**
 * Lifecycle of an established mentorship.
 *
 * <p>Legal transitions, enforced in the mentorship service:
 * <pre>
 *   ACTIVE -> ENDED   (either participant ends it)
 * </pre>
 *
 * <p>{@code ENDED} is terminal; V1 offers no reactivation. An ended mentorship
 * does not block the pair from requesting mentorship again later.
 */
public enum MentorshipStatus {

    /** An established mentorship between a mentor and a mentee. */
    ACTIVE,

    /** The mentorship was ended by one of its participants. */
    ENDED
}
