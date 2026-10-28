package com.ubit.mentorshiphub.common.security;

import java.util.Arrays;
import java.util.Locale;

import com.ubit.mentorshiphub.common.exception.InsufficientRoleException;
import com.ubit.mentorshiphub.common.exception.OnboardingRequiredException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;

/**
 * The reusable authorization gate for every feature area added later.
 *
 * <p>Access is decided by two independent dimensions, checked in a fixed order:
 * <ol>
 *   <li><b>Onboarding</b> - the account must hold a role. A null role grants
 *       nothing at all.</li>
 *   <li><b>Verification</b> (Gate 1) - the claimed status must be
 *       {@link VerificationStatus#VERIFIED}. Holding a role is a claim;
 *       participating requires an administrator to have confirmed it.</li>
 *   <li><b>Role capability</b> (Gate 2) - the role must permit the action.</li>
 * </ol>
 *
 * <p>Centralising this is what keeps future endpoints consistent: a new feature
 * cannot accidentally check the role without the verification status, or accept
 * a {@code VERIFIED} student as if the two were the same thing.
 *
 * <p>Stateless and side-effect free. It judges the {@link User} it is handed and
 * throws otherwise; it never queries the database, so it can be reused from
 * controllers, services and tests alike.
 */
public final class AuthorizationGates {

    private AuthorizationGates() {
    }

    /**
     * Whether the account has finished onboarding and therefore holds a role.
     * A null role means read-and-onboard only.
     */
    public static boolean hasCompletedOnboarding(User user) {
        return user.hasRole();
    }

    /**
     * Gate 1. Whether the account may take part in community interaction: it
     * holds a role <em>and</em> that role's claim has been verified.
     */
    public static boolean isVerifiedParticipant(User user) {
        return user.hasRole() && user.isVerified();
    }

    /**
     * Whether the account is cleared to perform an action reserved for the given
     * roles, i.e. Gate 1 followed by Gate 2.
     */
    public static boolean canActAs(User user, AppRole... permitted) {
        if (!isVerifiedParticipant(user)) {
            return false;
        }
        for (AppRole role : permitted) {
            if (role == user.getRole()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requires onboarding, throwing {@link OnboardingRequiredException} when the
     * account has no role yet.
     */
    public static User requireOnboardingComplete(User user) {
        if (!hasCompletedOnboarding(user)) {
            throw new OnboardingRequiredException(
                    "Complete onboarding before using this feature.");
        }
        return user;
    }

    /**
     * Requires Gate 1. An unverified account is refused here even when its role
     * would otherwise be permitted, so a pending or rejected student cannot post
     * a question or send a mentorship request.
     */
    public static User requireVerifiedParticipant(User user) {
        requireOnboardingComplete(user);
        if (!user.isVerified()) {
            throw new VerificationRequiredException(
                    "Your student or alumni status must be verified before you can do that. Current status: "
                            + describeStatus(user.getVerificationStatus()) + ".");
        }
        return user;
    }

    /**
     * Requires Gate 1 and then Gate 2: a verified account whose role is one of
     * {@code permitted}.
     */
    public static User requireRoles(User user, AppRole... permitted) {
        requireVerifiedParticipant(user);
        if (!canActAs(user, permitted)) {
            throw new InsufficientRoleException(
                    "This action is not available for your role. Required: "
                            + Arrays.toString(permitted) + ", yours: " + user.getRole() + ".");
        }
        return user;
    }

    private static String describeStatus(VerificationStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    /**
     * Maps a verification status to the wording the API and UI use, kept next to
     * the gate so the two cannot drift apart.
     */
    public static String statusLabel(VerificationStatus status) {
        return switch (status) {
            case UNVERIFIED -> "Not submitted";
            case PENDING -> "Awaiting review";
            case VERIFIED -> "Verified";
            case REJECTED -> "Rejected";
        };
    }

    /** Lower-cased status, for matching the enum values the frontend receives. */
    public static String statusToken(VerificationStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
