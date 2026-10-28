package com.ubit.mentorshiphub.mentorship.model;

/**
 * Lifecycle of a mentorship request.
 *
 * <p>Legal transitions, all enforced in the mentorship service:
 * <pre>
 *   PENDING   -> ACCEPTING  (recipient starts accepting; in-progress marker)
 *   ACCEPTING -> ACCEPTED   (the active mentorship has been written)
 *   ACCEPTING -> PENDING    (recovery reopens an interrupted acceptance that
 *                            never produced a mentorship)
 *   PENDING   -> REJECTED   (recipient declines)
 *   PENDING   -> CANCELLED  (requester withdraws)
 * </pre>
 *
 * <p>{@code ACCEPTING} is a short-lived, non-terminal <em>in-progress</em> state.
 * It exists because acceptance spans two collections (the request and the
 * mentorship) and a standalone {@code mongod} has no multi-document
 * transactions: the request is claimed with a conditional
 * {@code PENDING -> ACCEPTING} update <em>before</em> the mentorship is written,
 * so a competing accept, reject or cancel cannot change the request out from
 * under the winner. Only the acceptance path and the startup recovery move a
 * request out of {@code ACCEPTING}. A {@link MentorshipRequest} in this state is
 * always resolved to {@code ACCEPTED} or back to {@code PENDING} by
 * {@code MentorshipRecoveryService} after a restart.
 *
 * <p>{@code ACCEPTED}, {@code REJECTED} and {@code CANCELLED} are terminal: a
 * request in any of them can never transition again. Only the recipient may
 * accept or reject, and only the requester may cancel.
 */
public enum MentorshipRequestStatus {

    /** An invitation awaiting the recipient's decision. */
    PENDING,

    /**
     * The recipient is accepting and the active mentorship is being written.
     * Transient: recovered to {@code ACCEPTED} or {@code PENDING} on restart.
     */
    ACCEPTING,

    /** The recipient accepted; an active mentorship exists for the pair. */
    ACCEPTED,

    /** The recipient declined. Does not block a future request. */
    REJECTED,

    /** The requester withdrew before a decision. Does not block a future request. */
    CANCELLED
}
