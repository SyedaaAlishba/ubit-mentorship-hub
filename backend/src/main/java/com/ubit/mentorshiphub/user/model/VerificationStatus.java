package com.ubit.mentorshiphub.user.model;

/**
 * How far the platform has got in confirming an account's claimed student or
 * alumni status.
 *
 * <p>Independent of {@link AppRole}. A verification status never creates,
 * implies, or alters a role, and never blocks authentication - see
 * {@code docs/architecture/authentication-design.md} section 3.
 *
 * <p>Legal transitions, all enforced in the verification service:
 * <pre>
 *   UNVERIFIED -> PENDING            (user submits an application)
 *   PENDING    -> VERIFIED           (administrator approves)
 *   PENDING    -> REJECTED           (administrator rejects)
 *   REJECTED   -> PENDING            (user resubmits)
 * </pre>
 */
public enum VerificationStatus {

    /** No application has ever been submitted. */
    UNVERIFIED,

    /** An application is awaiting an administrator decision. */
    PENDING,

    /** An administrator approved the claim. */
    VERIFIED,

    /** An administrator declined the claim. The role is unchanged. */
    REJECTED;

    /** Whether the user may take part in community interaction (Gate 1). */
    public boolean isParticipant() {
        return this == VERIFIED;
    }

    /** Whether the account may submit or resubmit a verification application. */
    public boolean isApplicationStartable() {
        return this == UNVERIFIED || this == REJECTED;
    }
}
