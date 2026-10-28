package com.ubit.mentorshiphub.verification.model;

/**
 * The standing of one submitted application.
 *
 * <p>Separate from the account's {@code VerificationStatus} on purpose: the
 * account-level status moves as applications come and go, while each application
 * keeps its own outcome as an audit trail. Rejection is non-destructive, so a
 * later approval never erases the record of what was declined.
 */
public enum ApplicationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
