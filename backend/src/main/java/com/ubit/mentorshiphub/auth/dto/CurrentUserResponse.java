package com.ubit.mentorshiphub.auth.dto;

import com.ubit.mentorshiphub.common.security.AuthorizationGates;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;

/**
 * Response of {@code GET /api/auth/me}.
 *
 * <p>Onboarding happens in two steps: a role is chosen, then the claim behind
 * that role is confirmed by an administrator. Both are reported separately here,
 * so the frontend can show "student, awaiting review" instead of flattening two
 * independent facts into one label.
 *
 * @param state                   the one value the UI routes on
 * @param userId                  account id, or {@code null} when not signed in
 * @param email                   account email
 * @param name                    display name
 * @param role                    claimed role, {@code null} before onboarding
 * @param verificationStatus      verification standing, independent of the role
 * @param enabled                 whether the account may use the platform
 * @param onboardingComplete      whether a role has been chosen
 * @param canSubmitVerification   whether an application may be submitted or resubmitted
 * @param loginUrl                where the frontend sends the user to start Google sign-in
 * @param logoutUrl               the sign-out endpoint, POST with a CSRF token
 */
public record CurrentUserResponse(
        AuthState state,
        String userId,
        String email,
        String name,
        AppRole role,
        VerificationStatus verificationStatus,
        boolean enabled,
        boolean onboardingComplete,
        boolean canSubmitVerification,
        String loginUrl,
        String logoutUrl
) {

    public static final String LOGIN_URL = "/oauth2/authorization/google";
    public static final String LOGOUT_URL = "/api/auth/logout";

    /** The anonymous answer. No user fields are exposed. */
    public static CurrentUserResponse notAuthenticated() {
        return new CurrentUserResponse(
                AuthState.NOT_AUTHENTICATED, null, null, null, null, null,
                false, false, false, LOGIN_URL, LOGOUT_URL);
    }

    public static CurrentUserResponse of(User user) {
        boolean onboardingComplete = AuthorizationGates.hasCompletedOnboarding(user);
        boolean canSubmit = user.getVerificationStatus() != null
                && user.getVerificationStatus().isApplicationStartable();
        return new CurrentUserResponse(
                stateOf(user),
                user.getId() == null ? null : user.getId().toHexString(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getVerificationStatus(),
                user.isEnabled(),
                onboardingComplete,
                canSubmit,
                LOGIN_URL,
                LOGOUT_URL);
    }

    /**
     * A bootstrap or administrator-promoted account is VERIFIED while an ordinary
     * account follows its verification status - but only once it has a role. A
     * null role always reads as ONBOARDING_REQUIRED, whatever the status says,
     * so an un-onboarded account can never look ready to use the platform.
     */
    private static AuthState stateOf(User user) {
        if (!user.hasRole()) {
            return AuthState.ONBOARDING_REQUIRED;
        }
        // Distinguish between UNVERIFIED (role selected but no application) and PENDING (application submitted)
        return switch (user.getVerificationStatus()) {
            case PENDING -> AuthState.PENDING_VERIFICATION;
            case REJECTED -> AuthState.REJECTED;
            case VERIFIED -> AuthState.VERIFIED;
            case UNVERIFIED -> AuthState.VERIFICATION_REQUIRED;
        };
    }
}
