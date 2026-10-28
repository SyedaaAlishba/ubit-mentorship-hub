package com.ubit.mentorshiphub.auth.dto;

/**
 * The single state the frontend asks about. Exactly one of these is returned by
 * `GET /api/auth/me`, so the UI never has to infer the account's standing
 * from a combination of nullable fields.
 */
public enum AuthState {

    /** No session. The frontend shows the sign-in screen. */
    NOT_AUTHENTICATED,

    /** Signed in with no role yet. The frontend shows onboarding. */
    ONBOARDING_REQUIRED,

    /** Role selected but verification not yet submitted. Show verification form. */
    VERIFICATION_REQUIRED,

    /** An application is awaiting an administrator decision. */
    PENDING_VERIFICATION,

    /** An application was declined; the account may resubmit. */
    REJECTED,

    /** Onboarded and confirmed. The account may take part in the platform. */
    VERIFIED
}
