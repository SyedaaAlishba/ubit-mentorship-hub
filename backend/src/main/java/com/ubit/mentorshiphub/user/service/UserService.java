package com.ubit.mentorshiphub.user.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.common.exception.AccountNotFoundException;
import com.ubit.mentorshiphub.common.exception.EmailInUseException;
import com.ubit.mentorshiphub.common.exception.OnboardingConflictException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.repository.UserRepository;

/**
 * The single mutation point for {@link User}.
 *
 * <p>Every state change an account can undergo - creation, onboarding,
 * verification transitions, bootstrap admin, sign-in bookkeeping - is a named
 * method here, so the legal transitions are readable in one file and cannot be
 * reached accidentally from a controller or from Spring Security internals.
 *
 * <p>Authentication and verification services depend on this class, never on
 * {@link UserRepository}, which keeps the Controller -> Service -> Repository
 * layering intact and stops authorization rules from being bypassed.
 */
@Service
public class UserService {

    /**
     * Roles onboarding is allowed to set. {@code SENIOR} and {@code ADMIN} are
     * administrator-controlled and are structurally not offered, so a crafted
     * request body cannot obtain them.
     */
    private static final Set<AppRole> ONBOARDING_ROLES = Set.of(AppRole.STUDENT, AppRole.ALUMNI);

    /** Statuses an administrator may decide to. */
    private static final Set<VerificationStatus> DECISION_STATUSES =
            Set.of(VerificationStatus.VERIFIED, VerificationStatus.REJECTED);

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public Optional<User> findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    public Optional<User> findById(ObjectId id) {
        return id == null ? Optional.empty() : userRepository.findById(id);
    }

    public User requireById(ObjectId id) {
        return findById(id).orElseThrow(() -> new AccountNotFoundException(
                "No account exists for the current session."));
    }

    /**
     * Creates the account for a first-time Google identity.
     *
     * <p>Deliberately lands as {@code role = null} and
     * {@code verificationStatus = UNVERIFIED}: authentication proves identity,
     * never community status, so a new account is authenticated but not
     * authorized for anything beyond onboarding.
     */
    public User createAccount(String googleId, String email, String name) {
        Instant now = clock.instant();
        User user = new User();
        user.setGoogleId(googleId);
        user.setEmail(requireEmail(email));
        user.setName(normalizeName(name));
        user.setRole(null);
        user.setVerificationStatus(VerificationStatus.UNVERIFIED);
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        return persist(user);
    }

    /**
     * Records a successful sign-in for an account that already matched by
     * {@code sub}. Refreshes the mutable profile fields Google owns.
     *
     * <p>The email is only taken over when no other account already claims it,
     * so a name or email change on the Google side can never collide two
     * accounts through the unique index.
     */
    public User recordSignIn(User user, String googleId, String email, String name) {
        if (googleId != null && !googleId.equals(user.getGoogleId())) {
            user.setGoogleId(googleId);
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail != null && !normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            findByEmail(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new EmailInUseException(
                                "That email address already belongs to another account. Sign in with the original Google account.");
                    });
            user.setEmail(normalizedEmail);
        }
        if (name != null && !name.isBlank()) {
            user.setName(normalizeName(name));
        }
        Instant now = clock.instant();
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        return persist(user);
    }

    /**
     * Completes onboarding by assigning the self-selected role. Only
     * {@link AppRole#STUDENT} and {@link AppRole#ALUMNI} are accepted.
     *
     * <p>Onboarding claims identity, it does not confirm it: the account moves
     * to {@link VerificationStatus#PENDING} for an administrator to decide.
     */
    public User completeOnboarding(User user, AppRole requestedRole) {
        if (!ONBOARDING_ROLES.contains(requestedRole)) {
            throw new OnboardingConflictException(
                    "Onboarding accepts only CURRENT_STUDENT or ALUMNI.");
        }
        if (user.hasRole()) {
            if (user.getRole() == requestedRole && user.getVerificationStatus() == VerificationStatus.UNVERIFIED) {
                return user;
            }
            throw new OnboardingConflictException("Onboarding has already been completed for this account.");
        }
        if (user.getVerificationStatus() != VerificationStatus.UNVERIFIED) {
            throw new OnboardingConflictException(
                    "An application is already in progress for this account.");
        }
        user.setRole(requestedRole);
        user.setUpdatedAt(clock.instant());
        return persist(user);
    }

    /**
     * Moves the account into {@link VerificationStatus#PENDING} when a
     * verification application is submitted or resubmitted.
     *
     * <p>Legal from {@code UNVERIFIED} (first submission) and {@code REJECTED}
     * (resubmission). {@code PENDING} is not startable, so a pending application
     * cannot be replaced, and {@code VERIFIED} never regresses. The role is left
     * untouched on both paths, which is what makes rejection non-destructive.
     */
    public User startVerification(User user) {
        if (!user.getVerificationStatus().isApplicationStartable()) {
            throw new VerificationStateConflictException(
                    "A verification application cannot be submitted while the account status is "
                            + user.getVerificationStatus() + ".");
        }
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setVerificationReviewedAt(null);
        user.setUpdatedAt(clock.instant());
        return persist(user);
    }

    /**
     * Applies an administrator decision. Only {@code VERIFIED} and
     * {@code REJECTED} are reachable here, and either outcome leaves the role
     * exactly as it was.
     */
    public User applyVerificationDecision(User user, VerificationStatus decision) {
        if (!DECISION_STATUSES.contains(decision)) {
            throw new VerificationStateConflictException(
                    "Only VERIFIED or REJECTED can be applied as a verification decision.");
        }
        if (user.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new VerificationStateConflictException(
                    "Only an application awaiting review can be decided. Current status: "
                            + user.getVerificationStatus() + ".");
        }
        Instant now = clock.instant();
        user.setVerificationStatus(decision);
        user.setVerificationReviewedAt(now);
        user.setUpdatedAt(now);
        return persist(user);
    }

    /**
     * Grants the bootstrap administrator role and pre-verifies the account.
     *
     * <p>Intended to be called from the sign-in path only, and only for an
     * account being created right now - which is what stops the configured
     * address from silently re-promoting an account that an administrator has
     * since demoted.
     */
    public User grantBootstrapAdmin(User user) {
        user.setRole(AppRole.ADMIN);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setVerificationReviewedAt(clock.instant());
        user.setUpdatedAt(clock.instant());
        return persist(user);
    }

    private User persist(User user) {
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new EmailInUseException(
                    "That email address already belongs to another account. Sign in with the original Google account.");
        }
    }

    private static String requireEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            throw new IllegalArgumentException("Google did not return an email address.");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank()
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "UBIT Member" : name.trim();
    }
}
